package nik.kalomiris.inventory_service.listeners;

import nik.kalomiris.inventory_service.InventoryService;
import nik.kalomiris.inventory_service.config.RabbitMQConfig;
import nik.kalomiris.inventory_service.events.dtos.InventoryReservationFailedEvent;
import nik.kalomiris.inventory_service.events.dtos.InventoryReservedEvent;
import nik.kalomiris.inventory_service.events.dtos.OrderEvent;
import nik.kalomiris.inventory_service.events.dtos.OrderLineItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);
    private final InventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;

    public OrderEventListener(InventoryService inventoryService, RabbitTemplate rabbitTemplate) {
        this.inventoryService = inventoryService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE_NAME)
    public void handleOrderCreatedEvent(OrderEvent orderEvent) {
        logger.info("Received order created event for orderNumber: {}", orderEvent.getOrderNumber());
        List<InventoryReservedEvent.LineItem> reservedItems = new ArrayList<>();
        try {
            for (OrderLineItem item : orderEvent.getLineItems()) {
                try {
                    inventoryService.commitStock(item.getProductId(), item.getQuantity());
                    reservedItems.add(new InventoryReservedEvent.LineItem(item.getProductId(), item.getQuantity()));
                    logger.info("Committed stock for product ID: {} quantity: {}", item.getProductId(), item.getQuantity());
                } catch (Exception e) {
                    logger.error("Failed to commit stock for product ID: {}. Reason: {}", item.getProductId(), e.getMessage());
                    // Publish a failure event with reason and attempted items
                    InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                        orderEvent.getOrderNumber(),
                        orderEvent.getCorrelationId(),
                        Instant.now(),
                        "Failed to commit productId " + item.getProductId() + ": " + e.getMessage(),
                        reservedItems
                    );
                    rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED, failedEvent);
                    return; // stop processing further items
                }
            }

            // If we get here, all items were committed
                InventoryReservedEvent successEvent = new InventoryReservedEvent(
                    orderEvent.getOrderNumber(),
                    orderEvent.getCorrelationId(),
                    Instant.now(),
                    reservedItems
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVED, successEvent);
        
        } catch (Exception e) {
            logger.error("Unexpected error while processing orderNumber {}: {}", orderEvent.getOrderNumber(), e.getMessage());
            InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                orderEvent.getOrderNumber(),
                orderEvent.getCorrelationId(),
                Instant.now(),
                "Unexpected error: " + e.getMessage(),
                reservedItems
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED, failedEvent);
        }
    }
}
