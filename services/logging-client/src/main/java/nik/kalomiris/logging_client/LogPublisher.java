package nik.kalomiris.logging_client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public LogPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${logging.topic.service-logs:service-logs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(String message) {
        kafkaTemplate.send(topic, message);
    }
}
