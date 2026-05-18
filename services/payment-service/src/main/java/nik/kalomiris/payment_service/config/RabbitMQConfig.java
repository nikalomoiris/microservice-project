package nik.kalomiris.payment_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import nik.kalomiris.event_contracts.routing.PaymentExchangeContracts;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PaymentExchangeContracts.EXCHANGE_NAME);
    }

    @Bean
    public Queue paymentCreatedQueue() {
        return new Queue(PaymentExchangeContracts.PAYMENT_CREATED_QUEUE_NAME, true);
    }

    @Bean
    public Binding paymentCreatedBinding(Queue paymentCreatedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCreatedQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_CREATED);
    }

    @Bean
    public Queue paymentAuthorizationQueue() {
        return new Queue(PaymentExchangeContracts.PAYMENT_AUTHORIZATION_QUEUE_NAME, true);
    }

    @Bean
    public Binding paymentAuthorizationBinding(Queue paymentAuthorizationQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentAuthorizationQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_AUTHORIZED);
    }

    @Bean
    public Binding paymentAuthorizationFailedBinding(Queue paymentAuthorizationQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentAuthorizationQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_AUTHORIZATION_FAILED);
    }

    @Bean
    public Queue paymentRefundQueue() {
        return new Queue(PaymentExchangeContracts.PAYMENT_REFUND_QUEUE_NAME, true);
    }

    @Bean
    public Binding paymentRefundBinding(Queue paymentRefundQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentRefundQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_REFUNDED);
    }

    @Bean
    public Binding paymentRefundFailedBinding(Queue paymentRefundQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentRefundQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_REFUND_FAILED);
    }

    @Bean
    public Queue paymentCaptureQueue() {
        return new Queue(PaymentExchangeContracts.PAYMENT_CAPTURE_QUEUE_NAME, true);
    }

    @Bean
    public Binding paymentCaptureBinding(Queue paymentCaptureQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCaptureQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_CAPTURED);
    }

    @Bean
    public Binding paymentCaptureFailedBinding(Queue paymentCaptureQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCaptureQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_CAPTURE_FAILED);
    }

    @Bean
    public Queue paymentVoidQueue() {
        return new Queue(PaymentExchangeContracts.PAYMENT_VOID_QUEUE_NAME, true);
    }

    @Bean
    public Binding paymentVoidBinding(Queue paymentVoidQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentVoidQueue)
                .to(paymentExchange)
                .with(PaymentExchangeContracts.ROUTING_KEY_PAYMENT_VOIDED);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper rabbitObjectMapper) {
        return new Jackson2JsonMessageConverter(rabbitObjectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setObservationEnabled(true);
        return rabbitTemplate;
    }
}
