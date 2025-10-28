package nik.kalomiris.events.dtos;

public class OrderLineItem {
    /**
     * Simple line-item DTO used in order-related events. Only contains the
     * productId and quantity to keep the contract compact.
     */
    private Long productId;
    private Integer quantity;

    public OrderLineItem() {
    }

    public OrderLineItem(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
