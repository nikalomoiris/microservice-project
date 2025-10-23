package nik.kalomiris.product_service.product;


import nik.kalomiris.product_service.config.RabbitMQConfig;
import nik.kalomiris.logging_client.LogPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;

import nik.kalomiris.product_service.image.Image;
import nik.kalomiris.product_service.image.ImageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ImageRepository imageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final LogPublisher logPublisher;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper, 
            ImageRepository imageRepository, RabbitTemplate rabbitTemplate, LogPublisher logPublisher) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.imageRepository = imageRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
    }
    /**
     * Associates a new image with a product by productId and imageUrl (path or URL).
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
            logPublisher.publish("Image added to product " + productId + " url=" + imageUrl);
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
            products = productRepository.findByCategoryName(categoryName,sort);
        } else {
            products = productRepository.findAll(sort);
        }

        try {
            logPublisher.publish("Products retrieved");
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
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_PRODUCT_CREATED, savedProduct.getSku());

        // Publish a log event about the created product. Ignore logging failures.
        try {
            logPublisher.publish("Product created: sku=" + savedProduct.getSku() + " id=" + savedProduct.getId());
        } catch (Exception e) {
            // ignore logging failures
        }

        return productMapper.toDto(savedProduct);
    }

    public ProductDTO updateProduct(ProductDTO productDTO) {
        Product product = productMapper.toEntity(productDTO);
        Product updatedProduct = productRepository.save(product);
        try {
            logPublisher.publish("Product updated: id=" + updatedProduct.getId());
        } catch (Exception e) {
            // ignore logging failures
        }
        return productMapper.toDto(updatedProduct);
    }

    public void deleteProduct(Long id) {
        // Perform deletion; allow exceptions to propagate to caller.
        productRepository.deleteById(id);

        // Publish deletion log; ignore logging failures so deletion result is not affected.
        try {
            logPublisher.publish("Product deleted: id=" + id);
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
