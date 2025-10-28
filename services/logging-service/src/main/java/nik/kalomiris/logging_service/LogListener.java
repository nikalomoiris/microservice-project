package nik.kalomiris.logging_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LogListener {

    /**
     * Simple listener that reads structured log JSON messages from Kafka and
     * pretty-prints them to stdout. Implemented primarily for local/demo use;
     * production deployments should forward logs to a durable aggregator.
     */

    private final ObjectMapper objectMapper;

    public LogListener() {
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group")
    public void listen(String message) {
        try {
            // Parse the JSON message to pretty print it
            Object json = objectMapper.readValue(message, Object.class);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            System.out.println("[LOG] " + prettyJson);
        } catch (Exception e) {
            // If JSON parsing fails, log the raw message
            System.out.println("[LOG] (raw) " + message);
        }
    }
}
