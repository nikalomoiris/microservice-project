package nik.kalomiris.order_service.config;

import nik.kalomiris.logging_client.LogPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Provides a no-op LogPublisher when no LogPublisher bean is available on the
 * classpath.
 * This helps local development / docker-compose runs where the external logging
 * infrastructure
 * or client auto-configuration is not present.
 */
@Configuration
public class NoOpLogPublisherConfig {

    @Bean
    @ConditionalOnMissingBean(LogPublisher.class)
    public LogPublisher noOpLogPublisher(Environment env) {
        // If KafkaTemplate / real LogPublisher auto-configuration is not available,
        // create a minimal LogPublisher instance using a null KafkaTemplate and
        // configured topic. Calls to publish will be caught and ignored by callers.
        String topic = env.getProperty("logging.topic.service-logs", "service-logs");
        return new LogPublisher(null, topic, null);
    }
}
