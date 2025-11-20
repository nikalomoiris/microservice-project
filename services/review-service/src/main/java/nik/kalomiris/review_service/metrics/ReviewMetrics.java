package nik.kalomiris.review_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import nik.kalomiris.review_service.review.ReviewRepository;
import nik.kalomiris.review_service.review.ReviewStatus;

@Component
public class ReviewMetrics {

    private final MeterRegistry meterRegistry;
    private final ReviewRepository reviewRepository;
    private final AtomicLong lastReviewAddedEpoch = new AtomicLong(0);

    public ReviewMetrics(MeterRegistry meterRegistry, ReviewRepository reviewRepository) {
        this.meterRegistry = meterRegistry;
        this.reviewRepository = reviewRepository;
    }

    @PostConstruct
    public void register() {
        meterRegistry.gauge("review.count", reviewRepository, repo -> repo.count());
        meterRegistry.gauge("review.status.approved", reviewRepository,
                repo -> repo.findByStatus(ReviewStatus.APPROVED).size());
        meterRegistry.gauge("review.status.for_moderation", reviewRepository,
                repo -> repo.findByStatus(ReviewStatus.FOR_MODERATION).size());
        meterRegistry.gauge("review.last_added.timestamp", lastReviewAddedEpoch);
    }

    public void markReviewAdded() {
        meterRegistry.counter("review.created.count").increment();
        lastReviewAddedEpoch.set(Instant.now().getEpochSecond());
    }
}
