package nik.kalomiris.product_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "product-exchange";
    public static final String ROUTING_KEY_PRODUCT_CREATED = "product.created";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }
}