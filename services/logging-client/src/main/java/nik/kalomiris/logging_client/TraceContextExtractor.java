package nik.kalomiris.logging_client;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TraceContextExtractor {
    private final Tracer tracer; // may be null when tracing not configured in service

    public TraceContextExtractor(ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    public String getTraceId() {
        if (tracer == null)
            return null;
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    public String getSpanId() {
        if (tracer == null)
            return null;
        Span span = tracer.currentSpan();
        return span != null ? span.context().spanId() : null;
    }
}
