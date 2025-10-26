package nik.kalomiris.inventory_service.events.dtos;

import java.time.Instant;
import java.util.List;

public class InventoryReservedEvent {
    private String orderNumber;
    private String correlationId;
    private Instant timestamp;
    private List<LineItem> lineItems;

    public InventoryReservedEvent() {}

    public InventoryReservedEvent(String orderNumber, String correlationId, Instant timestamp, List<LineItem> lineItems) {
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

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public static class LineItem {
        private Long productId;
        private Integer quantity;

        public LineItem() {}

        public LineItem(Long productId, Integer quantity) {
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
    
}
