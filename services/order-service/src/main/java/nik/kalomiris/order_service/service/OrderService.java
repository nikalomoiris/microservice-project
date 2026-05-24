package nik.kalomiris.order_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.order_service.service.ProductServiceClient;
import nik.kalomiris.order_service.config.RabbitMQConfig;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.domain.OrderStatus;
import nik.kalomiris.event_contracts.dtos.OrderCreatedEvent;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.dto.ProductPrice;
import nik.kalomiris.order_service.mapper.OrderMapper;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.order_service.util.OrderStatusTransitions;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
     * RabbitMQ event only after the DB transaction commits (avoids races).
     * - Emits a structured log event using the project's logging client.
     */

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final LogPublisher logPublisher;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            ProductServiceClient productServiceClient,
            RabbitTemplate rabbitTemplate,
            LogPublisher logPublisher) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.productServiceClient = productServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.logPublisher = logPublisher;
    }

    // Backward-compatible constructor for tests
    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            RabbitTemplate rabbitTemplate,
            LogPublisher logPublisher) {
        this(orderRepository, orderMapper, null, rabbitTemplate, logPublisher);
    }

    public void createOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        validateLineItems(orderRequest);

        List<OrderLineItem> orderLineItems = orderRequest
                .getOrderLineItemsDtoList()
                .stream()
                .map(orderMapper::mapToOrderLineItem)
                .toList();

        order.setOrderLineItems(orderLineItems);

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderLineItem lineItem : orderLineItems) {
            ProductPrice productPrice = productServiceClient.getProduct(lineItem.getProductId());
            lineItem.setPrice(productPrice.price());
            lineItem.setSku(productPrice.sku());
            totalPrice = totalPrice.add(productPrice.price().multiply(BigDecimal.valueOf(lineItem.getQuantity())));
        }

        order.setTotalPrice(totalPrice);

        orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderNumber(),
                order.getOrderNumber(),
                order.getTotalPrice(),
                order.getCurrency(),
                Instant.now(),
                order.getOrderLineItems()
                        .stream()
                        .map(li -> new nik.kalomiris.event_contracts.dtos.OrderLineItem(li.getProductId(), li.getQuantity()))
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
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    public void confirmOrder(String orderNumber) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (OrderStatusTransitions.canTransitionTo(order.getStatus(), OrderStatus.CONFIRMED)) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else {
            throw new IllegalStateException(
                    "Cannot transition order to CONFIRMED from status: " + order.getStatus());
        }

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderNumber(),
                order.getOrderNumber(),
                order.getTotalPrice(),
                order.getCurrency(),
                Instant.now(),
                order.getOrderLineItems()
                        .stream()
                        .map(li -> new nik.kalomiris.event_contracts.dtos.OrderLineItem(li.getProductId(), li.getQuantity()))
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
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    private void validateLineItems(OrderRequest orderRequest) {
        orderRequest.getOrderLineItemsDtoList().forEach(itemDto -> {
            if (itemDto.getProductId() == null) {
                throw new IllegalArgumentException("Product ID is required for all order line items.");
            }
        });
    }
}
