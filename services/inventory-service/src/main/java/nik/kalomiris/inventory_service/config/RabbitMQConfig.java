package nik.kalomiris.inventory_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PRODUCT_EXCHANGE_NAME = "product-exchange";
    public static final String PRODUCT_CREATED_QUEUE_NAME = "inventory-service-queue";
    public static final String ROUTING_KEY_PRODUCT_CREATED = "product.created";

    public static final String ORDER_EXCHANGE_NAME = "order-exchange";
    public static final String ORDER_CREATED_QUEUE_NAME = "order.created.inventory.queue";
    public static final String ROUTING_KEY_ORDER_CREATED = "order.created";
    public static final String ROUTING_KEY_ORDER_INVENTORY_RESERVED = "order.inventory.reserved";
    public static final String ROUTING_KEY_ORDER_INVENTORY_RESERVATION_FAILED = "order.inventory.reservation_failed";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE_NAME);
    }

    @Bean
    public Queue inventoryQueue() {
        return new Queue(PRODUCT_CREATED_QUEUE_NAME, true);
    }

    @Bean
    public Binding productBinding(Queue inventoryQueue, TopicExchange productExchange) {
        return BindingBuilder.bind(inventoryQueue).to(productExchange).with(ROUTING_KEY_PRODUCT_CREATED);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE_NAME);
    }

    @Bean

    public Queue orderCreatedInventoryQueue() {
        return new Queue(ORDER_CREATED_QUEUE_NAME, true);
    }

    @Bean
    public Binding orderBinding(Queue orderCreatedInventoryQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedInventoryQueue).to(orderExchange).with(ROUTING_KEY_ORDER_CREATED);
    }
}