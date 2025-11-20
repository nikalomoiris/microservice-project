package nik.kalomiris.product_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import nik.kalomiris.product_service.product.ProductRepository;
import nik.kalomiris.product_service.category.CategoryRepository;

@Component
public class ProductMetrics {

    private final MeterRegistry meterRegistry;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AtomicLong lastProductAddedEpoch = new AtomicLong(0);

    public ProductMetrics(MeterRegistry meterRegistry,
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.meterRegistry = meterRegistry;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("product.count", productRepository, repo -> repo.count());
        meterRegistry.gauge("category.count", categoryRepository, repo -> repo.count());
        meterRegistry.gauge("product.last_added.timestamp", lastProductAddedEpoch);
    }

    public void markProductAdded() {
        meterRegistry.counter("product.created.count").increment();
        lastProductAddedEpoch.set(Instant.now().getEpochSecond());
    }
}
