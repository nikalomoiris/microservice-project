package nik.kalomiris.events.dtos;

public class ProductCreatedEvent {
    /**
     * Event published when a product is created in the product-service.
     * Other services (like inventory) consume this to create related records.
     */
    private Long productId;
    private String sku;

    public ProductCreatedEvent() {}

    public ProductCreatedEvent(Long productId, String sku) {
        this.productId = productId;
        this.sku = sku;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
