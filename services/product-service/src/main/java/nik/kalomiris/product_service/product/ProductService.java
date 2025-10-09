package nik.kalomiris.product_service.product;


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

    public ProductService(ProductRepository productRepository, ProductMapper productMapper, ImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.imageRepository = imageRepository;
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
    }

    public List<ProductDTO> getAllProducts() {
        // Stream.toList() returns an unmodifiable list (Java 16+)
        return productRepository.findAll().stream()
                .map(productMapper::toDto)
                .toList();
    }

    public Optional<ProductDTO> getProductById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toDto);
    }

    public ProductDTO createProduct(ProductDTO productDTO) {
        Product product = productMapper.toEntity(productDTO);

        // --- Business Logic Example ---
        // Generate a unique SKU before saving the product.
        String sku = generateSku(product.getName());
        product.setSku(sku);
        // --- End of Business Logic ---

        Product savedProduct = productRepository.save(product);
        return productMapper.toDto(savedProduct);
    }

    public ProductDTO updateProduct(ProductDTO productDTO) {
        Product product = productMapper.toEntity(productDTO);
        Product updatedProduct = productRepository.save(product);
        return productMapper.toDto(updatedProduct);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
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
