package nik.kalomiris.inventory_service.listeners;

import nik.kalomiris.inventory_service.InventoryService;
import nik.kalomiris.inventory_service.config.RabbitMQConfig;
import nik.kalomiris.events.dtos.ProductCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductEventListener {

    /**
     * Listens for ProductCreated events and ensures an Inventory record exists
     * for the new product. The create is idempotent so duplicate events are safe.
     */

    private static final Logger logger = LoggerFactory.getLogger(ProductEventListener.class);
    private final InventoryService inventoryService;

    public ProductEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = RabbitMQConfig.PRODUCT_CREATED_QUEUE_NAME)
    public void handleProductCreatedEvent(ProductCreatedEvent productEvent) {
        logger.info("Received product created event for SKU: {}", productEvent.getSku());
        // Create inventory record with the same ID as the product so order-service productId maps to inventory id.
        inventoryService.createInventoryRecord(productEvent.getProductId(), productEvent.getSku());
    }
}