package nik.kalomiris.logging_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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

    private final nik.kalomiris.logging_service.metrics.LoggingMetrics loggingMetrics;

    public LogListener(nik.kalomiris.logging_service.metrics.LoggingMetrics loggingMetrics) {
        this.objectMapper = new ObjectMapper();
        this.loggingMetrics = loggingMetrics;
    }

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group")
    public void listen(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            log.info("[LOG] {}", prettyJson);
            safeUpdateMetrics(json);
        } catch (Exception e) {
            log.info("[LOG] (raw) {}", message);
            safeMarkIngest();
        }
    }

    private void safeUpdateMetrics(JsonNode json) {
        try {
            loggingMetrics.markIngest();
            JsonNode levelNode = json.get("level");
            if (levelNode != null) {
                String level = levelNode.asText("");
                if ("ERROR".equalsIgnoreCase(level)) {
                    loggingMetrics.markError();
                } else if ("WARN".equalsIgnoreCase(level)) {
                    loggingMetrics.markWarn();
                }
            }
        } catch (Exception ignored) {
            /* metrics best-effort */
        }
    }

    private void safeMarkIngest() {
        try {
            loggingMetrics.markIngest();
        } catch (Exception ignored) {
            /* best-effort */
        }
    }
}
