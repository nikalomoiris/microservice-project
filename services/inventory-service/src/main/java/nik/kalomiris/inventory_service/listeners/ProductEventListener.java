package nik.kalomiris.inventory_service.listeners;

import nik.kalomiris.inventory_service.InventoryService;
import nik.kalomiris.inventory_service.config.RabbitMQConfig;
import nik.kalomiris.inventory_service.events.dtos.ProductCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProductEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductEventListener.class);
    private final InventoryService inventoryService;

    public ProductEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = RabbitMQConfig.PRODUCT_CREATED_QUEUE_NAME)
    public void handleProductCreatedEvent(ProductCreatedEvent productEvent) {
        logger.info("Received product created event for SKU: {}", productEvent.getSku());
        inventoryService.createInventoryRecord(productEvent.getSku());
    }
}