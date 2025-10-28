package nik.kalomiris.logging_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
