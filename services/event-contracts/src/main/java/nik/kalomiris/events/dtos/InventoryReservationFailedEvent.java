package nik.kalomiris.events.dtos;

import java.time.Instant;
import java.util.List;

public class InventoryReservationFailedEvent {
    /**
     * Event published when inventory reservation/commit fails for an order.
     * Contains a human-readable `reason` and the items that were attempted.
     */
    private String orderNumber;
    private String correlationId;
    private Instant timestamp;
    private String reason;
    private List<OrderLineItem> attemptedItems;

    public InventoryReservationFailedEvent() {}

    public InventoryReservationFailedEvent(String orderNumber, String correlationId, Instant timestamp, String reason, List<OrderLineItem> attemptedItems) {
        this.orderNumber = orderNumber;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.reason = reason;
        this.attemptedItems = attemptedItems;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<OrderLineItem> getAttemptedItems() {
        return attemptedItems;
    }

    public void setAttemptedItems(List<OrderLineItem> attemptedItems) {
        this.attemptedItems = attemptedItems;
    }
}
