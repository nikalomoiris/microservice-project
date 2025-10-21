package nik.kalomiris.logging_service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LogListener {

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group")
    public void listen(String message) {
        System.out.println("[LOG] " + message);
    }
}
