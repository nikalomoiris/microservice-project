package nik.kalomiris.logging_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("dev")
public class LogListener {

    /**
     * Simple listener that reads structured log JSON messages from Kafka and
     * pretty-prints them to stdout. Implemented primarily for local/demo use;
     * production deployments should forward logs to a durable aggregator.
     */

    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(LogListener.class);

    public LogListener() {
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group")
    public void listen(String message) {
        try {
            // Parse the JSON message to pretty print it
            Object json = objectMapper.readValue(message, Object.class);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            log.info("[LOG] {}", prettyJson);
        } catch (Exception e) {
            // If JSON parsing fails, log the raw message
            log.info("[LOG] (raw) {}", message);
        }
    }
}
