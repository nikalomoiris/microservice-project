package nik.kalomiris.order_service.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.order_service.config.RabbitMQConfig;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.dto.OrderPlacedEvent;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.mapper.OrderMapper;
import nik.kalomiris.order_service.repository.OrderRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

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
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItem> orderLineItems = orderRequest
            .getOrderLineItemsDtoList()
            .stream()
            .map(orderMapper::mapToOrderLineItem)
            .toList();

        order.setOrderLineItems(orderLineItems);

        orderRepository.save(order);

        OrderPlacedEvent event = new OrderPlacedEvent(order.getOrderNumber());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);

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
