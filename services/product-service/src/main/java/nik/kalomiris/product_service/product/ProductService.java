package nik.kalomiris.product_service.product;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product) {
        // --- Business Logic Example ---
        // Generate a unique SKU before saving the product.
        String sku = generateSku(product.getName());
        product.setSku(sku);
        // --- End of Business Logic ---

        return productRepository.save(product);
    }

    public Product updateProduct(Product product) {
        return productRepository.save(product);
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
