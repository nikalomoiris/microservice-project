package nik.kalomiris.order_service.domain;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "orders")
/**
 * Domain entity representing a customer's order.
 *
 * - `orderNumber` is an externally visible identifier (UUID string).
 * - `orderLineItems` holds the item rows. Cascade ALL is used so items are
 *   persisted with the owning Order.
 * - `status` tracks the order state using {@link OrderStatus}.
 * - `version` is used for optimistic locking to avoid concurrent write conflicts.
 */
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber;

    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderLineItem> orderLineItems;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;

    @Version
    private Long version;

    public Order() {}

    public Order(
        Long id,
        String orderNumber,
        List<OrderLineItem> orderLineItems
    ) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.orderLineItems = orderLineItems;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public List<OrderLineItem> getOrderLineItems() {
        return orderLineItems;
    }

    public void setOrderLineItems(List<OrderLineItem> orderLineItems) {
        this.orderLineItems = orderLineItems;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
