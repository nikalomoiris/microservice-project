package nik.kalomiris.order_service.service;

import lombok.RequiredArgsConstructor;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.dto.OrderPlacedEvent;
import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.mapper.OrderMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void createOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItem> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(orderMapper::mapToOrderLineItem)
                .toList();

        order.setOrderLineItems(orderLineItems);

        orderRepository.save(order);

        rabbitTemplate.convertAndSend(exchange, routingKey, new OrderPlacedEvent(order.getOrderNumber()));
    }

}
