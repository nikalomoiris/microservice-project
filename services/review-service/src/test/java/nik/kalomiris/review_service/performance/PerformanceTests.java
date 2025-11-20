package nik.kalomiris.review_service.performance;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.review_service.config.ReviewEvaluationConfig;
import nik.kalomiris.review_service.evaluation.ReviewEvaluationService;
import nik.kalomiris.review_service.review.Review;
import nik.kalomiris.review_service.similarity.CompositeSimilarityCalculator;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import nik.kalomiris.review_service.similarity.LevenshteinSimilarityCalculator;
import nik.kalomiris.review_service.similarity.TfidfCosineSimilarityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Performance tests for Review Similarity Evaluation System.
 * 
 * Requirements:
 * - Evaluation completes in < 200ms for 1000 reviews
 * - p95 review submission < 500ms
 * - Graceful degradation on evaluation failure
 */
class PerformanceTests {

    @Mock
    private ReviewEvaluationConfig config;

    @Mock
    private ReviewEvaluationConfig.Threshold threshold;

    @Mock
    private LogPublisher logPublisher;

    private ReviewEvaluationService evaluationService;
    private CompositeSimilarityCalculator similarityCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(config.getThreshold()).thenReturn(threshold);
        when(threshold.getApproved()).thenReturn(0.60);
        when(threshold.getModeration()).thenReturn(0.85);
        when(config.getMaxComparisons()).thenReturn(1000);
        when(config.isEnabled()).thenReturn(true);

        TfidfCosineSimilarityCalculator cosine = new TfidfCosineSimilarityCalculator();
        LevenshteinSimilarityCalculator levenshtein = new LevenshteinSimilarityCalculator();
        similarityCalculator = new CompositeSimilarityCalculator(cosine, levenshtein, 0.7, 0.3);

        evaluationService = new ReviewEvaluationService(similarityCalculator, config, logPublisher);
    }

    @Test
    void evaluateAgainst1000Reviews_shouldCompleteIn200ms() {
        List<Review> existingReviews = generate1000Reviews();
        Review newReview = createReview(1001L, "This is a new review to be evaluated");

        long startTime = System.nanoTime();
        EvaluationResult result = evaluationService.evaluate(newReview, existingReviews);
        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(elapsedMillis < 200,
                String.format("Evaluation took %dms, expected < 200ms", elapsedMillis));

        assertNotNull(result);
        System.out.printf("✓ Evaluated 1000 reviews in %dms (target: <200ms)%n", elapsedMillis);
    }

    @Test
    void p95Evaluation_shouldBeFast() {
        List<Review> existingReviews = generate100Reviews();
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            Review newReview = createReview(1000L + i, "Review number " + i);

            long startTime = System.nanoTime();
            evaluationService.evaluate(newReview, existingReviews);
            executionTimes.add((System.nanoTime() - startTime) / 1_000_000);
        }

        executionTimes.sort(Long::compareTo);
        long p95 = executionTimes.get(94);

        System.out.printf("p95: %dms (target: <500ms)%n", p95);
        assertTrue(p95 < 500);
    }

    @Test
    void evaluationFailure_gracefulDegradation() {
        Review existingReview = createReview(1L, null);
        Review newReview = createReview(2L, "Valid review");

        EvaluationResult result = evaluationService.evaluate(newReview, List.of(existingReview));

        assertNotNull(result);
        assertNotNull(result.getStatus());
    }

    private List<Review> generate1000Reviews() {
        return generateReviews(1000);
    }

    private List<Review> generate100Reviews() {
        return generateReviews(100);
    }

    private List<Review> generateReviews(int count) {
        List<Review> reviews = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            reviews.add(createReview((long) i, "Review #" + i));
        }
        return reviews;
    }

    private Review createReview(Long id, String comment) {
        Review review = new Review();
        review.setId(id);
        review.setProductId(1L);
        review.setComment(comment);
        review.setRating(5);
        return review;
    }
}
