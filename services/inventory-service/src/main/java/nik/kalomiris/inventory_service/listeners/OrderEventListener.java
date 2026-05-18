package nik.kalomiris.inventory_service.listeners;

import nik.kalomiris.inventory_service.InventoryService;
import nik.kalomiris.inventory_service.config.RabbitMQConfig;
import nik.kalomiris.event_contracts.dtos.InventoryReservationFailedEvent;
import nik.kalomiris.event_contracts.dtos.InventorySuccessEvent;
import nik.kalomiris.event_contracts.dtos.OrderCreatedEvent;
import nik.kalomiris.event_contracts.dtos.OrderLineItem;

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

    /**
     * Listens for OrderCreated events and attempts to reserve stock for each
     * ordered line item. On partial or full failure an InventoryReservationFailedEvent
     * is published so the originating service can react.
     *
     * This listener keeps processing simple and emits events for downstream
     * coordination via RabbitMQ.
     */

    private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);
    private final InventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;

    public OrderEventListener(InventoryService inventoryService, RabbitTemplate rabbitTemplate) {
        this.inventoryService = inventoryService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE_NAME)
    public void handleOrderCreatedEvent(OrderCreatedEvent orderCreatedEvent) {
        logger.info("Received order created event for orderNumber: {}", orderCreatedEvent.getOrderNumber());
        List<OrderLineItem> reservedItems = new ArrayList<>();
            try {
                for (OrderLineItem item : orderCreatedEvent.getLineItems()) {
                    try {
                        inventoryService.reserveStock(item.getProductId(), item.getQuantity());
                        reservedItems.add(new OrderLineItem(item.getProductId(), item.getQuantity()));
                        logger.info("Reserved stock for product ID: {} quantity: {}", item.getProductId(), item.getQuantity());
                    } catch (Exception e) {
                        logger.error("Failed to reserve stock for product ID: {}. Reason: {}", item.getProductId(), e.getMessage());
                        // Publish a failure event with reason and attempted items
                        InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                            orderCreatedEvent.getOrderNumber(),
                            orderCreatedEvent.getCorrelationId(),
                            Instant.now(),
                            "Failed to reserve productId " + item.getProductId() + ": " + e.getMessage(),
                            reservedItems
                        );
                        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED, failedEvent);
                        return; // stop processing further items
                    }
                }

                // If we get here, all items were reserved
                InventorySuccessEvent successEvent = new InventorySuccessEvent(
                        orderCreatedEvent.getOrderNumber(),
                        orderCreatedEvent.getCorrelationId(),
                        Instant.now(),
                        reservedItems
                    );
                    rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVED, successEvent);
            
            } catch (Exception e) {
                logger.error("Unexpected error while processing orderNumber {}: {}", orderCreatedEvent.getOrderNumber(), e.getMessage());
                InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                    orderCreatedEvent.getOrderNumber(),
                    orderCreatedEvent.getCorrelationId(),
                    Instant.now(),
                    "Unexpected error: " + e.getMessage(),
                    reservedItems
                );
                rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED, failedEvent);
            }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CONFIRMED_QUEUE_NAME)
    public void handleOrderConfirmedEvent(OrderCreatedEvent orderCreatedEvent) {
        logger.info("Received order confirmed event for orderNumber: {}", orderCreatedEvent.getOrderNumber());
        // Try to commit the reserved stock. If commit fails, log error. If commit succeeds, send message to confirm.
        List<OrderLineItem> committedItems = new ArrayList<>();
        try {
            for (OrderLineItem item : orderCreatedEvent.getLineItems()) {
                inventoryService.commitStock(item.getProductId(), item.getQuantity());
                logger.info("Committed stock for product ID: {} quantity: {}", item.getProductId(), item.getQuantity());
                committedItems.add(item);
            }
            InventorySuccessEvent committedEvent = new InventorySuccessEvent(
                orderCreatedEvent.getOrderNumber(),
                orderCreatedEvent.getCorrelationId(),
                Instant.now(),
                committedItems
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_INVENTORY_COMMITTED, committedEvent);
        } catch (Exception e) {
            logger.error("Failed to commit stock for orderNumber {}: {}", orderCreatedEvent.getOrderNumber(), e.getMessage());
        }
    }
}
