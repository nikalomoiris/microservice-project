package nik.kalomiris.order_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import nik.kalomiris.order_service.repository.OrderRepository;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;
    private final OrderRepository orderRepository;
    private final AtomicLong lastOrderCreatedEpoch = new AtomicLong(0);

    public OrderMetrics(MeterRegistry meterRegistry, OrderRepository orderRepository) {
        this.meterRegistry = meterRegistry;
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("order.count", orderRepository, repo -> repo.count());
        meterRegistry.gauge("order.last_created.timestamp", lastOrderCreatedEpoch);
    }

    public void markOrderCreated() {
        meterRegistry.counter("order.created.count").increment();
        lastOrderCreatedEpoch.set(Instant.now().getEpochSecond());
    }
}
