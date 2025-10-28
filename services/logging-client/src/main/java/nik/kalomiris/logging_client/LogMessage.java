package nik.kalomiris.logging_client;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogMessage {
    /**
     * Represents a structured log payload that is serialized to JSON and
     * published to the logging topic. Fields are optional to keep messages
     * compact; metadata can be used to attach structured key/value pairs.
     */
    private String timestamp;
    private String level;
    private String service;
    private String message;
    private String logger;
    private String thread;
    private String traceId;
    private String spanId;
    private Map<String, Object> metadata;

    public LogMessage() {
        this.timestamp = Instant.now().toString();
        this.thread = Thread.currentThread().getName();
    }

    public LogMessage(String message) {
        this();
        this.message = message;
    }

    // Getters and setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Builder pattern for convenience
    public static class Builder {
        private final LogMessage logMessage;

        public Builder() {
            this.logMessage = new LogMessage();
        }

        public Builder message(String message) {
            logMessage.setMessage(message);
            return this;
        }

        public Builder level(String level) {
            logMessage.setLevel(level);
            return this;
        }

        public Builder service(String service) {
            logMessage.setService(service);
            return this;
        }

        public Builder logger(String logger) {
            logMessage.setLogger(logger);
            return this;
        }

        public Builder traceId(String traceId) {
            logMessage.setTraceId(traceId);
            return this;
        }

        public Builder spanId(String spanId) {
            logMessage.setSpanId(spanId);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            logMessage.setMetadata(metadata);
            return this;
        }

        public LogMessage build() {
            return logMessage;
        }
    }
}
