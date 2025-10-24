package nik.kalomiris.logging_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper;

    private LogPublisher logPublisher;

    @Test
    void testPublishPlainStringMessage() throws Exception {
        // Arrange
        logPublisher = new LogPublisher(kafkaTemplate, "test-topic");
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        logPublisher.publish("Test log message");

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("test-topic"), messageCaptor.capture());
        String sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);
        assertTrue(sentMessage.contains("Test log message"));
        assertTrue(sentMessage.contains("\"message\""));
        
        // Verify it's valid JSON
        ObjectMapper mapper = new ObjectMapper();
        LogMessage parsedMessage = mapper.readValue(sentMessage, LogMessage.class);
        assertEquals("Test log message", parsedMessage.getMessage());
        assertNotNull(parsedMessage.getTimestamp());
        assertNotNull(parsedMessage.getThread());
    }

    @Test
    void testPublishStructuredLogMessage() throws Exception {
        // Arrange
        logPublisher = new LogPublisher(kafkaTemplate, "test-topic");
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        
        LogMessage logMessage = new LogMessage.Builder()
                .message("Product created")
                .level("INFO")
                .service("product-service")
                .logger("nik.kalomiris.product_service.ProductService")
                .traceId("trace-123")
                .spanId("span-456")
                .metadata(Map.of("productId", "42", "sku", "ABC-123"))
                .build();

        // Act
        logPublisher.publish(logMessage);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("test-topic"), messageCaptor.capture());
        String sentMessage = messageCaptor.getValue();
        
        // Verify it's valid JSON with all fields
        ObjectMapper mapper = new ObjectMapper();
        LogMessage parsedMessage = mapper.readValue(sentMessage, LogMessage.class);
        assertEquals("Product created", parsedMessage.getMessage());
        assertEquals("INFO", parsedMessage.getLevel());
        assertEquals("product-service", parsedMessage.getService());
        assertEquals("nik.kalomiris.product_service.ProductService", parsedMessage.getLogger());
        assertEquals("trace-123", parsedMessage.getTraceId());
        assertEquals("span-456", parsedMessage.getSpanId());
        assertNotNull(parsedMessage.getMetadata());
        assertEquals("42", parsedMessage.getMetadata().get("productId"));
        assertEquals("ABC-123", parsedMessage.getMetadata().get("sku"));
    }

    @Test
    void testPublishLogMessageWithMinimalFields() throws Exception {
        // Arrange
        logPublisher = new LogPublisher(kafkaTemplate, "test-topic");
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        
        LogMessage logMessage = new LogMessage("Simple message");

        // Act
        logPublisher.publish(logMessage);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("test-topic"), messageCaptor.capture());
        String sentMessage = messageCaptor.getValue();
        
        // Verify JSON only contains non-null fields
        ObjectMapper mapper = new ObjectMapper();
        LogMessage parsedMessage = mapper.readValue(sentMessage, LogMessage.class);
        assertEquals("Simple message", parsedMessage.getMessage());
        assertNotNull(parsedMessage.getTimestamp());
        assertNotNull(parsedMessage.getThread());
        assertNull(parsedMessage.getLevel());
        assertNull(parsedMessage.getService());
        assertNull(parsedMessage.getTraceId());
    }

    @Test
    void testBuilderPattern() {
        // Test the builder pattern
        LogMessage logMessage = new LogMessage.Builder()
                .message("Test message")
                .level("DEBUG")
                .service("test-service")
                .logger("TestLogger")
                .traceId("trace-999")
                .spanId("span-888")
                .metadata(Map.of("key", "value"))
                .build();

        assertEquals("Test message", logMessage.getMessage());
        assertEquals("DEBUG", logMessage.getLevel());
        assertEquals("test-service", logMessage.getService());
        assertEquals("TestLogger", logMessage.getLogger());
        assertEquals("trace-999", logMessage.getTraceId());
        assertEquals("span-888", logMessage.getSpanId());
        assertNotNull(logMessage.getMetadata());
        assertEquals("value", logMessage.getMetadata().get("key"));
    }
}
