package nik.kalomiris.order_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_line_items")
/**
 * Entity representing a single line item within an Order.
 *
 * Fields:
 * - `sku` and `price` are read from products at time of ordering.
 * - `quantity` is how many units were ordered.
 * - `productId` links back to the product-service identifier and is required
 *   by business logic when reserving inventory.
 */
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private BigDecimal price;
    private Integer quantity;
    private Long productId; // NEW

    public OrderLineItem() {}

    public OrderLineItem(
        Long id,
        String sku,
        BigDecimal price,
        Integer quantity,
        Long productId
    ) {
        this.id = id;
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
        this.productId = productId;
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

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
