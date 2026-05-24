package nik.kalomiris.logging_client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class LogPublisherTest {

    @Test
    void publishesJsonMessageToKafka() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);

        LogPublisher publisher = new LogPublisher(kafkaTemplate, "service-logs");

        LogMessage msg = new LogMessage.Builder()
                .message("hello")
                .service("test-svc")
                .build();

        publisher.publish(msg);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq("service-logs"), captor.capture());

        String json = captor.getValue();
        assertTrue(json.contains("\"message\":\"hello\""));
        assertTrue(json.contains("\"service\":\"test-svc\""));
    }
}
