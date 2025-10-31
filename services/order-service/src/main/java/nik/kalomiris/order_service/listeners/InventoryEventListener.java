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
import nik.kalomiris.events.dtos.InventoryReservationFailedEvent;
import nik.kalomiris.events.dtos.InventoryReservedEvent;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.order_service.util.RetryUtils;
import nik.kalomiris.order_service.util.OrderStatusTransitions;

@Component
public class InventoryEventListener {

    /**
     * Component listening to inventory-related integration events from RabbitMQ.
     *
     * It updates local Order state based on inventory outcomes. The listener
     * methods are transactional and use optimistic-lock retry helper when
     * updating the order to avoid lost updates under concurrent processing.
     */

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
        if (OrderStatusTransitions.canTransitionTo(order.getStatus(), OrderStatus.RESERVED)) {
            RetryUtils.retryOnOptimisticLock(() -> {
                Order toUpdate = orderRepository.findById(order.getId()).orElseThrow();
                toUpdate.setStatus(OrderStatus.RESERVED);
                orderRepository.save(toUpdate);
                return null;
            }, 3, 100);
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
        if (OrderStatusTransitions.canTransitionTo(order.getStatus(), OrderStatus.RESERVATION_FAILED)) {
            RetryUtils.retryOnOptimisticLock(() -> {
                Order toUpdate = orderRepository.findById(order.getId()).orElseThrow();
                toUpdate.setStatus(OrderStatus.RESERVATION_FAILED);
                orderRepository.save(toUpdate);
                return null;
            }, 3, 100);
            logger.info("Order {} status updated to RESERVATION_FAILED", order.getOrderNumber());
        } else {
            logger.warn("Unexpected state transition for order {}: {} - RESERVATION_FAILED", order.getOrderNumber(), order.getStatus());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_INVENTORY_COMMITTED_QUEUE)
    @Transactional
    public void handleInventoryCommitted(InventoryReservedEvent event) {
        logger.info("Received InventoryCommittedEvent for order {}", event.getOrderNumber());
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(event.getOrderNumber());
        if (orderOpt.isEmpty()) {
            logger.warn("Order not found: {}", event.getOrderNumber());
            return;
        }
        Order order = orderOpt.get();
        // Idempotency: if already CONFIRMED, SHIPPED or COMPLETED, ignore
        if (order.getStatus() == OrderStatus.COMMITTED
            ||order.getStatus() == OrderStatus.CONFIRMED 
            || order.getStatus() == OrderStatus.SHIPPED 
            || order.getStatus() == OrderStatus.COMPLETED) {
            logger.info("Order {} already in status {}, ignoring event", order.getOrderNumber(), order.getStatus());
            return;
        }
        // Only allow CONFIRMED -> COMMITTED
        if (OrderStatusTransitions.canTransitionTo(order.getStatus(), OrderStatus.COMMITTED)) {
            RetryUtils.retryOnOptimisticLock(() -> {
                Order toUpdate = orderRepository.findById(order.getId()).orElseThrow();
                toUpdate.setStatus(OrderStatus.COMMITTED);
                orderRepository.save(toUpdate);
                return null;
            }, 3, 100);
            logger.info("Order {} status updated to COMMITTED", order.getOrderNumber());
        } else {
            logger.warn("Unexpected state transition for order {}: {} - COMMITTED", order.getOrderNumber(), order.getStatus());
        }
    }
    
}
