package nik.kalomiris.logging_client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public LogPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${logging.topic.service-logs:service-logs}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Publishes a plain string message to Kafka (legacy support).
     * @param message The plain text message to publish
     */
    public void publish(String message) {
        // For backward compatibility, wrap string in LogMessage
        LogMessage logMessage = new LogMessage(message);
        publish(logMessage);
    }

    /**
     * Publishes a structured log message as JSON to Kafka.
     * @param logMessage The structured log message to publish
     */
    public void publish(LogMessage logMessage) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(logMessage);
            kafkaTemplate.send(topic, jsonMessage);
        } catch (JsonProcessingException e) {
            // Fall back to plain message if serialization fails
            String fallbackMessage = String.format("{\"message\":\"%s\",\"error\":\"Serialization failed: %s\"}", 
                    logMessage.getMessage(), e.getMessage());
            kafkaTemplate.send(topic, fallbackMessage);
        }
    }
}
