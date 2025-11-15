# Structured JSON Logging

The logging-client module now supports structured JSON logging to Kafka, providing better log aggregation and analysis capabilities.

## Features

- Structured log messages with standard fields (timestamp, level, service, message, logger, thread)
- Support for distributed tracing (traceId, spanId)
- Custom metadata for additional context
- Backward compatibility with plain string messages
- Automatic timestamp and thread information

## Usage

### Basic Usage (Plain String - Legacy Support)

```java
@Service
public class MyService {
    private final LogPublisher logPublisher;
    
    public MyService(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }
    
    public void doSomething() {
        // Plain string messages are automatically wrapped in structured format
        logPublisher.publish("Something happened");
    }
}
```

### Structured Logging with Builder Pattern

```java
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import java.util.Map;

@Service
public class ProductService {
    private final LogPublisher logPublisher;
    
    public ProductService(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }
    
    public void createProduct(String sku, Long productId) {
        LogMessage logMessage = new LogMessage.Builder()
                .message("Product created")
                .level("INFO")
                .service("product-service")
                .logger("nik.kalomiris.product_service.product.ProductService")
                .metadata(Map.of(
                    "sku", sku,
                    "productId", productId.toString()
                ))
                .build();
        
        logPublisher.publish(logMessage);
    }
}
```

### With Distributed Tracing

```java
LogMessage logMessage = new LogMessage.Builder()
        .message("Order processed")
        .level("INFO")
        .service("order-service")
        .logger("nik.kalomiris.order_service.OrderService")
        .traceId("trace-abc123")
        .spanId("span-def456")
        .metadata(Map.of("orderId", "42", "userId", "7"))
        .build();

logPublisher.publish(logMessage);
```

## JSON Schema

Log messages are published to Kafka in the following JSON format:

```json
{
  "timestamp": "2025-10-23T22:38:10Z",
  "level": "INFO",
  "service": "product-service",
  "message": "Product created",
  "logger": "nik.kalomiris.product_service.product.ProductService",
  "thread": "http-nio-8080-exec-1",
  "traceId": "abcd1234",
  "spanId": "efgh5678",
  "metadata": {
    "productId": "42",
    "sku": "ABC-123"
  }
}
```

### Field Descriptions

- **timestamp**: ISO 8601 timestamp (automatically set)
- **level**: Log level (e.g., INFO, DEBUG, WARN, ERROR)
- **service**: Name of the microservice
- **message**: Human-readable log message
- **logger**: Fully qualified class name of the logger
- **thread**: Thread name (automatically set)
- **traceId**: Distributed tracing trace ID (optional)
- **spanId**: Distributed tracing span ID (optional)
- **metadata**: Additional key-value pairs for context (optional)

## Notes

- Fields with `null` values are automatically excluded from the JSON output
- Timestamp and thread are automatically populated when creating a LogMessage
- The LogPublisher handles JSON serialization errors gracefully by sending a fallback message
- All logging calls should be wrapped in try-catch blocks to prevent logging failures from affecting business logic

## Indexing to OpenSearch (local dev)

When running the local observability stack, logs from the Kafka topic `service-logs` are written to an OpenSearch index `service-logs` via Kafka Connect.

- Expected index pattern: `service-logs*`
- Time field: prefer `@timestamp` if present; otherwise `timestamp`
- Mapping highlights:
    - `level`, `service`, `logger`, `thread`, `traceId`, `spanId` are `keyword`
    - `message` is `text`
    - `metadata` is a dynamic object (arbitrary fields)

Recommendations for producers:
- Always include `service` and `level`.
- Include `traceId`/`spanId` when available for cross-service correlation.
- Put structured details into `metadata` (strings or numbers are best for filtering).

See `ops/opensearch/index-templates/service-logs-template.json` and `ops/connectors/service-logs-opensearch.json` for the local pipeline configuration.

## Migration from String-based Logging

Existing code using plain string messages will continue to work without modification. However, for better observability and log analysis, consider migrating to structured logging:

**Before:**
```java
logPublisher.publish("Product created: sku=" + sku + " id=" + id);
```

**After:**
```java
LogMessage logMessage = new LogMessage.Builder()
        .message("Product created")
        .level("INFO")
        .service("product-service")
        .logger(this.getClass().getName())
        .metadata(Map.of("sku", sku, "productId", id.toString()))
        .build();
logPublisher.publish(logMessage);
```
