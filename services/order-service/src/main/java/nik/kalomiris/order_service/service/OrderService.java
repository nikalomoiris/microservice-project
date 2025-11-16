package nik.kalomiris.order_service.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.order_service.config.RabbitMQConfig;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.domain.OrderStatus;
import nik.kalomiris.events.dtos.OrderEvent;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.mapper.OrderMapper;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.order_service.util.OrderStatusTransitions;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

@Service
@Transactional
public class OrderService {
    private static final String ERROR_TAG_KEY = "error";

    /**
     * Service responsible for creating orders and coordinating side effects
     * such as publishing integration events and emitting structured logs.
     *
     * Design notes:
     * - Persists the Order entity within a transaction.
     * - Registers a transaction synchronization to publish the order-created
     * RabbitMQ event only after the DB transaction commits (avoids races).
     * - Emits a structured log event using the project's logging client.
     */

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final LogPublisher logPublisher;
    private final Tracer tracer;

    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            RabbitTemplate rabbitTemplate,
            LogPublisher logPublisher,
            Tracer tracer) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
        this.tracer = tracer;
    }

    public void createOrder(OrderRequest orderRequest) {
        /**
         * Create a new Order from the incoming request.
         *
         * Responsibilities:
         * - validate required fields (productId present on line items)
         * - persist the Order and its line items
         * - publish an OrderEvent to RabbitMQ after successful commit
         * - publish a log event (best-effort)
         */
        Span span = tracer != null ? tracer.currentSpan() : null;
        if (span != null) {
            span.tag("order.request.item_count", String.valueOf(orderRequest.getOrderLineItemsDtoList().size()));
        }

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        // Validate line items contain productId
        try {
            orderRequest.getOrderLineItemsDtoList().forEach(itemDto -> {
                if (itemDto.getProductId() == null) {
                    throw new IllegalArgumentException("Product ID is required for all order line items.");
                }
            });
        } catch (Exception e) {
            if (span != null) {
                span.error(e);
                span.tag(ERROR_TAG_KEY, "true");
            }
            throw e;
        }

        List<OrderLineItem> orderLineItems = orderRequest
                .getOrderLineItemsDtoList()
                .stream()
                .map(orderMapper::mapToOrderLineItem)
                .toList();

        order.setOrderLineItems(orderLineItems);

        try {
            orderRepository.save(order);
        } catch (Exception e) {
            if (span != null) {
                span.error(e);
                span.tag(ERROR_TAG_KEY, "true");
            }
            throw e;
        }

        OrderEvent event = new OrderEvent(
                order.getOrderNumber(),
                order.getOrderNumber(),
                Instant.now(),
                order.getOrderLineItems()
                        .stream()
                        .map(li -> new nik.kalomiris.events.dtos.OrderLineItem(li.getProductId(), li.getQuantity()))
                        .toList());

        // Ensure we publish the event only after the database transaction commits so
        // consumers won't receive the event before the order is visible in the DB.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                            RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
                }
            });
        } else {
            // No transaction active (e.g., tests or manual call) — send immediately.
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED,
                    event);
        }

        // Publish a log event about the order creation. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Order created")
                    .level("INFO")
                    .service("order-service")
                    .logger("nik.kalomiris.order_service.service.OrderService")
                    .metadata(Map.of("orderNumber", order.getOrderNumber(), "itemCount",
                            String.valueOf(orderLineItems.size())))
                    .build();
            logPublisher.publish(logMessage);
            if (span != null) {
                span.tag("order.number", order.getOrderNumber());
                span.tag("order.items", String.valueOf(orderLineItems.size()));
            }
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    public void confirmOrder(String orderNumber) {

        Span span = tracer != null ? tracer.currentSpan() : null;
        Order order;
        try {
            order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        } catch (Exception e) {
            if (span != null) {
                span.error(e);
                span.tag(ERROR_TAG_KEY, "true");
            }
            throw e;
        }

        if (OrderStatusTransitions.canTransitionTo(order.getStatus(), OrderStatus.CONFIRMED)) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else {
            IllegalStateException e = new IllegalStateException(
                    "Cannot transition order to CONFIRMED from status: " + order.getStatus());
            if (span != null) {
                span.error(e);
                span.tag(ERROR_TAG_KEY, "true");
            }
            throw e;
        }

        OrderEvent event = new OrderEvent(
                order.getOrderNumber(),
                order.getOrderNumber(),
                Instant.now(),
                order.getOrderLineItems()
                        .stream()
                        .map(li -> new nik.kalomiris.events.dtos.OrderLineItem(li.getProductId(), li.getQuantity()))
                        .toList());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CONFIRMED, event);

        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Order confirmation received")
                    .level("INFO")
                    .service("order-service")
                    .logger("nik.kalomiris.order_service.service.OrderService")
                    .metadata(Map.of("orderNumber", order.getOrderNumber()))
                    .build();
            logPublisher.publish(logMessage);
            if (span != null) {
                span.tag("order.number", order.getOrderNumber());
                span.tag("order.status", order.getStatus().name());
            }
        } catch (Exception e) {
            // ignore logging failures

        }
    }

}
