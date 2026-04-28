package nik.kalomiris.logging_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * Main entry point for the Logging Service.
 *
 * Quick flow reference:
 * Kafka consumer -> Logging pipeline service -> sink/output formatting
 * -> OpenSearch indexing or stdout forwarding for local debugging.
 *
 * Observability hooks in the flow:
 * - service-level logs for pipeline health
 * - tracing context propagation when available in incoming payloads
 */
public class LoggingServiceApplication {
    /**
     * Small service that consumes structured log messages (e.g. from Kafka)
     * and emits them to stdout or another sink. Useful for local development
     * and demonstrations of structured logging.
     */
    public static void main(String[] args) {
        SpringApplication.run(LoggingServiceApplication.class, args);
    }
}
