package nik.kalomiris.product_service.product;

import nik.kalomiris.product_service.config.RabbitMQConfig;
import nik.kalomiris.events.dtos.ProductCreatedEvent;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;

import nik.kalomiris.product_service.image.Image;
import nik.kalomiris.product_service.image.ImageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {
    private static final String SERVICE_NAME = "product-service";
    private static final String LOGGER_NAME = "nik.kalomiris.product_service.product.ProductService";
    private static final String PRODUCT_ID_KEY = "productId";

    /**
     * Business logic for products. Responsible for creating products,
     * producing ProductCreated events, and mapping between entity and DTO.
     * Service methods throw domain-specific exceptions that controllers map
     * to HTTP responses.
     */

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ImageRepository imageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final LogPublisher logPublisher;
    private final nik.kalomiris.product_service.metrics.ProductMetrics productMetrics;

    @Autowired
    public ProductService(ProductRepository productRepository, ProductMapper productMapper,
            ImageRepository imageRepository, RabbitTemplate rabbitTemplate, LogPublisher logPublisher,
            nik.kalomiris.product_service.metrics.ProductMetrics productMetrics) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.imageRepository = imageRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
        this.productMetrics = productMetrics;
    }

    // Backward-compatible constructor without metrics
    public ProductService(ProductRepository productRepository, ProductMapper productMapper,
            ImageRepository imageRepository, RabbitTemplate rabbitTemplate, LogPublisher logPublisher) {
        this(productRepository, productMapper, imageRepository, rabbitTemplate, logPublisher, null);
    }

    /**
     * Associates a new image with a product by productId and imageUrl (path or
     * URL).
     */
    @Transactional
    public void addImageToProduct(Long productId, String imageUrl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        Image image = new Image();
        image.setUrl(imageUrl);
        image.setProduct(product);
        product.getImages().add(image);
        // Save image (cascade on product should also work, but explicit save is safe)
        imageRepository.save(image);
        // Publish a log event about the image addition. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Image added to product")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(PRODUCT_ID_KEY, productId.toString(), "imageUrl", imageUrl))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    public List<ProductDTO> getAllProducts() {
        return getAllProducts(null, null, "asc");
    }

    public List<ProductDTO> getAllProducts(String categoryName, String sortBy, String sortDir) {
        Sort sort = (sortBy != null && !sortBy.isEmpty())
                ? Sort.by(Sort.Direction.fromString(sortDir), sortBy)
                : Sort.unsorted();

        List<Product> products;
        if (categoryName != null && !categoryName.isEmpty()) {
            products = productRepository.findByCategoryName(categoryName, sort);
        } else {
            products = productRepository.findAll(sort);
        }

        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Products retrieved")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(categoryName != null ? Map.of("categoryName", categoryName) : null)
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return products.stream()
                .map(productMapper::toDto)
                .toList();
    }

    public Optional<ProductDTO> getProductById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toDto);
    }

    public ProductDTO createProduct(ProductDTO productDTO) {
        Product product = productMapper.toEntity(productDTO);

        // Generate a unique SKU before saving the product.
        String sku = generateSku(product.getName());
        product.setSku(sku);

        Product savedProduct = productRepository.save(product);

        // Send a message to RabbitMQ
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_PRODUCT_CREATED,
                new ProductCreatedEvent(savedProduct.getId(), savedProduct.getSku()));

        // Publish a log event about the created product. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Product created")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of("sku", savedProduct.getSku(), PRODUCT_ID_KEY, savedProduct.getId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        // Metrics: increment created counter and update last-added timestamp
        try {
            if (productMetrics != null) {
                productMetrics.markProductAdded();
            }
        } catch (Exception ignored) {
            /* metrics update is best-effort and should not affect business flow */
        }

        return productMapper.toDto(savedProduct);
    }

    public ProductDTO updateProduct(ProductDTO productDTO) {
        Product product = productMapper.toEntity(productDTO);
        Product updatedProduct = productRepository.save(product);
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Product updated")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(PRODUCT_ID_KEY, updatedProduct.getId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }
        return productMapper.toDto(updatedProduct);
    }

    public void deleteProduct(Long id) {
        // Perform deletion; allow exceptions to propagate to caller.
        productRepository.deleteById(id);

        // Publish deletion log; ignore logging failures so deletion result is not
        // affected.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Product deleted")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(PRODUCT_ID_KEY, id.toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    public boolean productExists(Long id) {
        return productRepository.existsById(id);
    }

    /**
     * Generates a SKU from the product name.
     * Logic: First 3 letters of the name (uppercase) + a random 4-digit number.
     */
    private String generateSku(String productName) {
        String prefix = "GEN"; // Default prefix
        if (productName != null && productName.length() >= 3) {
            prefix = productName.substring(0, 3).toUpperCase();
        }
        int randomNumber = ThreadLocalRandom.current().nextInt(1000, 10000);
        return prefix + "-" + randomNumber;
    }
}
