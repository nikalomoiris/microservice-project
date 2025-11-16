package nik.kalomiris.logging_client;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TraceContextExtractorTest {

    @Test
    void returnsNullsWhenNoTracer() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> provider = (ObjectProvider<Tracer>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        TraceContextExtractor extractor = new TraceContextExtractor(provider);
        assertNull(extractor.getTraceId());
        assertNull(extractor.getSpanId());
    }

    @Test
    void extractsIdsWhenSpanPresent() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        io.micrometer.tracing.TraceContext ctx = mock(io.micrometer.tracing.TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> provider = (ObjectProvider<Tracer>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracer);

        TraceContextExtractor extractor = new TraceContextExtractor(provider);
        assertEquals("trace-123", extractor.getTraceId());
        assertEquals("span-456", extractor.getSpanId());
    }
}
