package nik.kalomiris.inventory_service.events.dtos;

public class ProductCreatedEvent {
    private String sku;

    public ProductCreatedEvent() {
    }

    public ProductCreatedEvent(String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
