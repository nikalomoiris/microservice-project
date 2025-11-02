package nik.kalomiris.order_service.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "order-exchange";
    public static final String ROUTING_KEY_ORDER_CREATED = "order.created";
    public static final String ROUTING_KEY_ORDER_CONFIRMED = "order.confirmed";
    
    public static final String ORDER_INVENTORY_RESERVED_QUEUE = "order.inventory.reserved.queue";
    public static final String ORDER_INVENTORY_RESERVATION_FAILED_QUEUE = "order.inventory.reservation_failed.queue";
    public static final String ORDER_INVENTORY_COMMITTED_QUEUE = "order.inventory.committed.queue";
    public static final String ROUTING_KEY_ORDER_INVENTORY_RESERVED = "order.inventory.reserved";
    public static final String ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED = "order.inventory.reservation_failed";
    public static final String ROUTING_KEY_ORDER_INVENTORY_COMMITTED = "order.inventory.committed";

    @Bean
    public Queue orderInventoryReservedQueue() {
        return new Queue(ORDER_INVENTORY_RESERVED_QUEUE, true);
    }

    @Bean
    public Binding reservedBinding(Queue orderInventoryReservedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderInventoryReservedQueue)
                .to(exchange)
                .with(ROUTING_KEY_ORDER_INVENTORY_RESERVED);
    }

    @Bean
    public Queue orderInventoryReservationFailedQueue() {
        return new Queue(ORDER_INVENTORY_RESERVATION_FAILED_QUEUE, true);
    }

    @Bean
    public Binding failedBinding(Queue orderInventoryReservationFailedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderInventoryReservationFailedQueue)
                .to(exchange)
                .with(ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED);
    }

    @Bean
    public Queue orderInventoryCommittedQueue() {
        return new Queue(ORDER_INVENTORY_COMMITTED_QUEUE, true);
    }

    @Bean
    public Binding committedBinding(Queue orderInventoryCommittedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderInventoryCommittedQueue)
                .to(exchange)
                .with(ROUTING_KEY_ORDER_INVENTORY_COMMITTED);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    @Primary
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
