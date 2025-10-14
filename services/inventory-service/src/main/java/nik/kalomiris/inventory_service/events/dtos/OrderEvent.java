package nik.kalomiris.inventory_service.events.dtos;

import java.util.List;

public class OrderEvent {
    private String orderId;
    private List<OrderLineItem> lineItems;

    public OrderEvent() {
    }

    public OrderEvent(String orderId, List<OrderLineItem> lineItems) {
        this.orderId = orderId;
        this.lineItems = lineItems;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems;
    }
}
