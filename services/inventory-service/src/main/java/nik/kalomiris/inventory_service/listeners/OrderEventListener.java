package nik.kalomiris.inventory_service.listeners;

import nik.kalomiris.inventory_service.InventoryService;
import nik.kalomiris.inventory_service.config.RabbitMQConfig;
import nik.kalomiris.inventory_service.events.dtos.OrderEvent;
import nik.kalomiris.inventory_service.events.dtos.OrderLineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);
    private final InventoryService inventoryService;

    public OrderEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE_NAME)
    public void handleOrderCreatedEvent(OrderEvent orderEvent) {
        logger.info("Received order created event for order ID: {}", orderEvent.getOrderId());
        for (OrderLineItem item : orderEvent.getLineItems()) {
            try {
                inventoryService.commitStock(item.getProductId(), item.getQuantity());
                logger.info("Committed stock for product ID: {} quantity: {}", item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                logger.error("Failed to commit stock for product ID: {}. Reason: {}", item.getProductId(), e.getMessage());
                // Here you might want to publish a compensating event to handle the failure
            }
        }
    }
}
