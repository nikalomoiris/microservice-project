package nik.kalomiris.order_service.listeners;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import nik.kalomiris.order_service.config.RabbitMQConfig;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderStatus;
import nik.kalomiris.order_service.dto.InventoryReservationFailedEvent;
import nik.kalomiris.order_service.dto.InventoryReservedEvent;
import nik.kalomiris.order_service.repository.OrderRepository;

@Component
public class InventoryEventListener {

    private static final Logger logger = LoggerFactory.getLogger(InventoryEventListener.class);
    private final OrderRepository orderRepository;

    public InventoryEventListener(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_INVENTORY_RESERVED_QUEUE)
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        logger.info("Received InventoryReservedEvent for order {}", event.getOrderNumber());
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(event.getOrderNumber());
        if (orderOpt.isEmpty()) {
            logger.warn("Order not found: {}", event.getOrderNumber());
            return;
        }
        Order order = orderOpt.get();
        // Idempotency: if already RESERVED or beyond, ignore
        if (order.getStatus() == OrderStatus.RESERVED || order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.COMPLETED) {
            logger.info("Order {} already in status {}, ignoring event", order.getOrderNumber(), order.getStatus());
            return;
        }
        // Only allow CREATED -> RESERVED
        if (order.getStatus() == OrderStatus.CREATED) {
            order.setStatus(OrderStatus.RESERVED);
            orderRepository.save(order);
            logger.info("Order {} status updated to RESERVED", order.getOrderNumber());
        } else {
            logger.warn("Unexpected state transition for order {}: {} - RESERVED", order.getOrderNumber(), order.getStatus());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_INVENTORY_RESERVATION_FAILED_QUEUE)
    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        logger.info("Received InventoryReservationFailedEvent for order {} reason: {}", event.getOrderNumber(), event.getReason());
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(event.getOrderNumber());
        if (orderOpt.isEmpty()) {
            logger.warn("Order not found: {}", event.getOrderNumber());
            return;
        }
        Order order = orderOpt.get();
        // Idempotency: if already failed, ignore
        if (order.getStatus() == OrderStatus.RESERVATION_FAILED) {
            logger.info("Order {} already in status RESERVATION_FAILED, ignoring event", order.getOrderNumber());
            return;
        }
        // Only allow CREATED -> RESERVATION_FAILED (or PARTIALLY_RESERVED -> RESERVATION_FAILED)
        if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PARTIALLY_RESERVED) {
            order.setStatus(OrderStatus.RESERVATION_FAILED);
            orderRepository.save(order);
            logger.info("Order {} status updated to RESERVATION_FAILED", order.getOrderNumber());
        } else {
            logger.warn("Unexpected state transition for order {}: {} - RESERVATION_FAILED", order.getOrderNumber(), order.getStatus());
        }
    }
    
}
