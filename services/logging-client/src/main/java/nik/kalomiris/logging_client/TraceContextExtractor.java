package nik.kalomiris.logging_client;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Utility for extracting trace context from Micrometer Tracer.
 * <p>
 * This component is designed to work with or without tracing configured.
 * If no {@link Tracer} bean is available, all methods will return {@code null}.
 * </p>
 */
@Component
public class TraceContextExtractor {
    private final Tracer tracer; // may be null when tracing not configured in service

    /**
     * Constructs a TraceContextExtractor.
     * <p>
     * The tracerProvider may not provide a Tracer bean if tracing is not configured;
     * in that case, {@code tracer} will be null and all methods will return null.
     * </p>
     *
     * @param tracerProvider ObjectProvider for Tracer, may be absent (null Tracer)
     */
    public TraceContextExtractor(@Nullable ObjectProvider<Tracer> tracerProvider) {
        this.tracer = tracerProvider.getIfAvailable();
    }

    public String getTraceId() {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    public String getSpanId() {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        return span != null ? span.context().spanId() : null;
    }
}
