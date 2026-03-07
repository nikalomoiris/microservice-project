package nik.kalomiris.order_service.exception;

/**
 * Exception thrown when product validation fails during order creation.
 * 
 * Scenarios:
 * - Product not found in product-service
 * - Product-service is unavailable
 * - Network error while validating product
 * 
 * This is a RuntimeException so Spring will automatically roll back
 * the transaction if thrown during order creation.
 */
public class ProductValidationException extends RuntimeException {
    public ProductValidationException(String message) {
        super(message);
    }
}
