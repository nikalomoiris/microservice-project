
package nik.kalomiris.inventory_service;

public class InventoryDTO {
    private String sku;
    private Integer quantity;
    private boolean inStock;

    public InventoryDTO() {
    }

    public InventoryDTO(String sku, Integer quantity, boolean inStock) {
        this.sku = sku;
        this.quantity = quantity;
        this.inStock = inStock;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }
}
