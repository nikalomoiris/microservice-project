package nik.kalomiris.order_service.dto;

import java.math.BigDecimal;

/**
 * Immutable record representing the pricing information for a product.
 * Used when validating product prices during order creation.
 */
public record ProductPrice(BigDecimal price, String sku) {
}
