package nik.kalomiris.inventory_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import nik.kalomiris.inventory_service.InventoryRepository;

@Component
public class InventoryMetrics {

    private final MeterRegistry meterRegistry;
    private final InventoryRepository inventoryRepository;
    private final AtomicLong lastUpdateEpoch = new AtomicLong(0);

    public InventoryMetrics(MeterRegistry meterRegistry, InventoryRepository inventoryRepository) {
        this.meterRegistry = meterRegistry;
        this.inventoryRepository = inventoryRepository;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("inventory.product.count", inventoryRepository, repo -> repo.count());
        meterRegistry.gauge("inventory.last_update.timestamp", lastUpdateEpoch);
    }

    public void markUpdated() {
        lastUpdateEpoch.set(Instant.now().getEpochSecond());
    }
}
