package nik.kalomiris.logging_client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogPublisher {

    /**
     * Lightweight client for publishing structured log messages to Kafka.
     *
     * Provides backward-compatible support for plain string messages as
     * well as structured {@link LogMessage} objects serialized as JSON.
     */

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final TraceContextExtractor traceExtractor;

    public LogPublisher(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${logging.topic.service-logs:service-logs}") String topic,
            TraceContextExtractor traceExtractor) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        this.traceExtractor = traceExtractor;
    }

    /**
     * Publishes a plain string message to Kafka (legacy support).
     * 
     * @param message The plain text message to publish
     */
    public void publish(String message) {
        // For backward compatibility, wrap string in LogMessage
        LogMessage logMessage = new LogMessage(message);
        publish(logMessage);
    }

    /**
     * Publishes a structured log message as JSON to Kafka.
     * 
     * @param logMessage The structured log message to publish
     */
    public void publish(LogMessage logMessage) {
        // Auto-inject trace context when available and not already set
        if (logMessage.getTraceId() == null) {
            String traceId = traceExtractor != null ? traceExtractor.getTraceId() : null;
            if (traceId != null) {
                logMessage.setTraceId(traceId);
            }
        }
        if (logMessage.getSpanId() == null) {
            String spanId = traceExtractor != null ? traceExtractor.getSpanId() : null;
            if (spanId != null) {
                logMessage.setSpanId(spanId);
            }
        }
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
