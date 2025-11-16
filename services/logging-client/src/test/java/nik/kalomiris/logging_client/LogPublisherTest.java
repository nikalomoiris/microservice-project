package nik.kalomiris.logging_client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class LogPublisherTest {

    @Test
    void autoInjectsTraceContextWhenMissing() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        TraceContextExtractor extractor = mock(TraceContextExtractor.class);
        when(extractor.getTraceId()).thenReturn("trace-abc");
        when(extractor.getSpanId()).thenReturn("span-def");

        LogPublisher publisher = new LogPublisher(kafkaTemplate, "service-logs", extractor);

        LogMessage msg = new LogMessage.Builder()
                .message("hello")
                .service("test-svc")
                .build();

        publisher.publish(msg);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(eq("service-logs"), captor.capture());

        String json = captor.getValue();
        assertTrue(json.contains("\"traceId\":\"trace-abc\""));
        assertTrue(json.contains("\"spanId\":\"span-def\""));
    }

    @Test
    void preservesExistingTraceContext() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        TraceContextExtractor extractor = mock(TraceContextExtractor.class);
        when(extractor.getTraceId()).thenReturn("trace-ignored");
        when(extractor.getSpanId()).thenReturn("span-ignored");

        LogPublisher publisher = new LogPublisher(kafkaTemplate, "service-logs", extractor);

        LogMessage msg = new LogMessage.Builder()
                .message("hello")
                .service("test-svc")
                .traceId("trace-explicit")
                .spanId("span-explicit")
                .build();

        publisher.publish(msg);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("service-logs"), captor.capture());
        String json = captor.getValue();
        assertTrue(json.contains("\"traceId\":\"trace-explicit\""));
        assertTrue(json.contains("\"spanId\":\"span-explicit\""));
    }
}
