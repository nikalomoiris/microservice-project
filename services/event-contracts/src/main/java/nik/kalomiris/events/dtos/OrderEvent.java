package nik.kalomiris.events.dtos;

import java.time.Instant;
import java.util.List;

public class OrderEvent {
    private String orderNumber;
    private String correlationId;
    private Instant timestamp;
    private List<OrderLineItem> lineItems;

    public OrderEvent() {}

    public OrderEvent(String orderNumber, String correlationId, Instant timestamp, List<OrderLineItem> lineItems) {
        this.orderNumber = orderNumber;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.lineItems = lineItems;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems;
    }
}
