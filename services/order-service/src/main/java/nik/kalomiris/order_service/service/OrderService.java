package nik.kalomiris.order_service.service;

import java.util.List;
import java.util.UUID;
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

    public OrderService(
        OrderRepository orderRepository,
        OrderMapper orderMapper,
        RabbitTemplate rabbitTemplate
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
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
    }
}
