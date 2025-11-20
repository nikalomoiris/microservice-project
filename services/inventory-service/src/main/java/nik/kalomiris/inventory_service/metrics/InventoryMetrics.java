package nik.kalomiris.inventory_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import nik.kalomiris.inventory_service.InventoryRepository;

@Component
public class InventoryMetrics {

    private final MeterRegistry meterRegistry;
    private final InventoryRepository inventoryRepository;
    private final AtomicLong lastUpdateEpoch = new AtomicLong(0);
    private static final int LOW_STOCK_THRESHOLD = 10;

    public InventoryMetrics(MeterRegistry meterRegistry, InventoryRepository inventoryRepository) {
        this.meterRegistry = meterRegistry;
        this.inventoryRepository = inventoryRepository;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("inventory.product.count",
                inventoryRepository, repo -> repo.count());
        meterRegistry.gauge("inventory.last_update.timestamp",
                lastUpdateEpoch);
        meterRegistry.gauge("inventory.total_quantity",
                inventoryRepository, repo -> Optional
                        .ofNullable(repo.sumTotalQuantity()).orElse(0L).doubleValue());
        meterRegistry.gauge("inventory.reserved_quantity",
                inventoryRepository, repo -> Optional
                        .ofNullable(repo.sumReservedQuantity()).orElse(0L).doubleValue());
        meterRegistry.gauge("inventory.low_stock.count",
                inventoryRepository, repo -> repo.countLowStock(LOW_STOCK_THRESHOLD));
        meterRegistry.gauge("inventory.out_of_stock.count",
                inventoryRepository, repo -> repo.countOutOfStock());
    }

    public void markUpdated() {
        lastUpdateEpoch.set(Instant.now().getEpochSecond());
    }
}
