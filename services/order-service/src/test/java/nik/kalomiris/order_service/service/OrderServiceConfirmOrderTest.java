package nik.kalomiris.order_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.order_service.config.RabbitMQConfig;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderLineItem;
import nik.kalomiris.order_service.domain.OrderStatus;
import nik.kalomiris.order_service.repository.OrderRepository;
import nik.kalomiris.events.dtos.OrderEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceConfirmOrderTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private LogPublisher logPublisher;

    // OrderMapper is not used by confirmOrder, mock it to satisfy constructor
    @Mock
    private nik.kalomiris.order_service.mapper.OrderMapper orderMapper;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
    orderService = new OrderService(orderRepository, orderMapper, rabbitTemplate, logPublisher);
    }

    @Test
    void confirmOrder_whenOrderIsCommitted_transitionsToConfirmedAndPublishesEvent() {
    // Arrange: order currently RESERVED -> allowed transition to CONFIRMED per OrderStatusTransitions
    Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("order-committed-1");
        order.setOrderLineItems(List.of(new OrderLineItem(1L, "sku-1", BigDecimal.valueOf(10), 2, 123L)));
    order.setStatus(OrderStatus.RESERVED);

        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        orderService.confirmOrder(order.getOrderNumber());

        // Assert: status changed and persistence and messaging occurred
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.ROUTING_KEY_ORDER_CONFIRMED), eventCaptor.capture());
        OrderEvent sent = eventCaptor.getValue();
        assertEquals(order.getOrderNumber(), sent.getOrderNumber());

        // verify logging attempted
        verify(logPublisher).publish(any(LogMessage.class));
    }

    @Test
    void confirmOrder_whenOrderNotFound_throwsIllegalArgumentException() {
        when(orderRepository.findByOrderNumber("missing-order")).thenReturn(Optional.empty());

    String missing = "missing-order";
    assertThrows(IllegalArgumentException.class, () -> orderService.confirmOrder(missing));
    }

    @Test
    void confirmOrder_whenTransitionNotAllowed_throwsIllegalStateException() {
        Order order = new Order();
        order.setId(2L);
        order.setOrderNumber("order-created-1");
        order.setOrderLineItems(List.of());
        order.setStatus(OrderStatus.CREATED); // CREATED cannot transition to CONFIRMED

        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

    String orderNum = order.getOrderNumber();
    assertThrows(IllegalStateException.class, () -> orderService.confirmOrder(orderNum));
    }
}
