package nik.kalomiris.logging_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class LoggingMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong lastIngestEpoch = new AtomicLong(0);

    public LoggingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("log.last_ingest.timestamp", lastIngestEpoch);
    }

    public void markIngest() {
        lastIngestEpoch.set(Instant.now().getEpochSecond());
        meterRegistry.counter("log.ingest.count").increment();
    }

    public void markError() {
        meterRegistry.counter("log.error.count").increment();
    }

    public void markWarn() {
        meterRegistry.counter("log.warn.count").increment();
    }
}
