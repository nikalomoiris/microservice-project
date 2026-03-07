package nik.kalomiris.order_service.dto;

import java.math.BigDecimal;

public class OrderLineItemsDto {
    private Long id;
    private Integer quantity;
    private Long productId;

    public OrderLineItemsDto() {
    }

    public OrderLineItemsDto(Long id, Integer quantity, Long productId) {
        this.id = id;
        this.quantity = quantity;
        this.productId = productId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
