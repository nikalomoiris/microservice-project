package nik.kalomiris.order_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Lightweight DTO for deserializing product information from product-service.
 * 
 * Only includes the fields needed by order-service (price, sku).
 * JsonIgnoreProperties ensures we ignore extra fields from the API response
 * (like description, categoryIds, imageUrls, etc.) that we don't need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductInfoDto {
    private BigDecimal price;
    private String sku;

    public ProductInfoDto() {
        // Required by Jackson for JSON deserialization
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
