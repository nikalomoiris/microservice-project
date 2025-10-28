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
import nik.kalomiris.events.dtos.OrderEvent;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.mapper.OrderMapper;
import nik.kalomiris.order_service.repository.OrderRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    /**
     * Service responsible for creating orders and coordinating side effects
     * such as publishing integration events and emitting structured logs.
     *
     * Design notes:
     * - Persists the Order entity within a transaction.
     * - Registers a transaction synchronization to publish the order-created
     *   RabbitMQ event only after the DB transaction commits (avoids races).
     * - Emits a structured log event using the project's logging client.
     */

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final LogPublisher logPublisher;

    public OrderService(
        OrderRepository orderRepository,
        OrderMapper orderMapper,
        RabbitTemplate rabbitTemplate,
        LogPublisher logPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
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
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        // Validate line items contain productId
        orderRequest.getOrderLineItemsDtoList().forEach(itemDto -> {
                if (itemDto.getProductId() == null) {
                    throw new IllegalArgumentException("Product ID is required for all order line items.");
                }
            }
        );

        List<OrderLineItem> orderLineItems = orderRequest
            .getOrderLineItemsDtoList()
            .stream()
            .map(orderMapper::mapToOrderLineItem)
            .toList();

        order.setOrderLineItems(orderLineItems);

        orderRepository.save(order);

        OrderEvent event = new OrderEvent(
            order.getOrderNumber(),
            order.getOrderNumber(),
            Instant.now(),
            order.getOrderLineItems()
                .stream()
                .map(li -> new nik.kalomiris.events.dtos.OrderLineItem(li.getProductId(), li.getQuantity()))
                .toList()
        );

        // Ensure we publish the event only after the database transaction commits so
        // consumers won't receive the event before the order is visible in the DB.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
                }
            });
        } else {
            // No transaction active (e.g., tests or manual call) — send immediately.
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
        }

        // Publish a log event about the order creation. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Order created")
                    .level("INFO")
                    .service("order-service")
                    .logger("nik.kalomiris.order_service.service.OrderService")
                    .metadata(Map.of("orderNumber", order.getOrderNumber(), "itemCount", String.valueOf(orderLineItems.size())))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }
    }
}
