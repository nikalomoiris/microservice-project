package nik.kalomiris.order_service.dto;

import java.math.BigDecimal;

public class OrderLineItemsDto {
    private Long id;
    private String sku;
    private BigDecimal price;
    private Integer quantity;

    public OrderLineItemsDto() {
    }

    public OrderLineItemsDto(Long id, String sku, BigDecimal price, Integer quantity) {
        this.id = id;
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
