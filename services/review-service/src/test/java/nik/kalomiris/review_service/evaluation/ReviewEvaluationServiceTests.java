package nik.kalomiris.review_service.evaluation;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.review_service.config.ReviewEvaluationConfig;
import nik.kalomiris.review_service.review.Review;
import nik.kalomiris.review_service.review.ReviewStatus;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import nik.kalomiris.review_service.similarity.SimilarityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReviewEvaluationService}.
 * 
 * Tests cover:
 * - Feature flag behavior
 * - Empty review set handling
 * - Similarity threshold logic
 * - Edge cases and error handling
 */
class ReviewEvaluationServiceTests {

    @Mock
    private SimilarityCalculator similarityCalculator;

    @Mock
    private ReviewEvaluationConfig config;

    @Mock
    private ReviewEvaluationConfig.Threshold threshold;

    @Mock
    private LogPublisher logPublisher;

    private ReviewEvaluationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup default config mocks
        when(config.getThreshold()).thenReturn(threshold);
        when(threshold.getApproved()).thenReturn(0.60);
        when(threshold.getModeration()).thenReturn(0.85);
        when(config.getMaxComparisons()).thenReturn(1000);
        when(config.isEnabled()).thenReturn(true);

        service = new ReviewEvaluationService(similarityCalculator, config, logPublisher);
    }

    @Test
    void evaluateWithFeatureFlagDisabled_returnsApproved() {
        // Given: evaluation disabled
        when(config.isEnabled()).thenReturn(false);
        Review newReview = createReview(1L, "Great product");
        List<Review> existing = Collections.emptyList();

        // When
        EvaluationResult result = service.evaluate(newReview, existing);

        // Then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.0, result.getSimilarityScore());
        assertTrue(result.getEvaluationReason().contains("disabled"));
        verify(similarityCalculator, never()).similarity(anyString(), anyString());
    }

    @Test
    void evaluateWithNoExistingReviews_returnsApproved() {
        // Given: first review for product
        Review newReview = createReview(1L, "First review");
        List<Review> existing = Collections.emptyList();

        // When
        EvaluationResult result = service.evaluate(newReview, existing);

        // Then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.0, result.getSimilarityScore());
        assertNull(result.getMostSimilarReviewId());
        assertTrue(result.getEvaluationReason().contains("First review"));
    }

    @Test
    void evaluateWithLowSimilarity_returnsApproved() {
        // Given: low similarity (< 0.60)
        Review newReview = createReview(1L, "Unique content");
        Review existing = createReview(2L, "Different content");
        when(similarityCalculator.similarity(anyString(), anyString())).thenReturn(0.3);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(existing));

        // Then
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.3, result.getSimilarityScore(), 0.001);
        assertEquals(2L, result.getMostSimilarReviewId());
        assertTrue(result.getEvaluationReason().contains("Unique"));
    }

    @Test
    void evaluateWithModerateSimilarity_returnsForModeration() {
        // Given: moderate similarity (0.60 <= score < 0.85)
        Review newReview = createReview(1L, "Good product");
        Review existing = createReview(2L, "Good product with minor difference");
        when(similarityCalculator.similarity(anyString(), anyString())).thenReturn(0.75);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(existing));

        // Then
        assertEquals(ReviewStatus.FOR_MODERATION, result.getStatus());
        assertEquals(0.75, result.getSimilarityScore(), 0.001);
        assertEquals(2L, result.getMostSimilarReviewId());
        assertTrue(result.getEvaluationReason().contains("Similar"));
    }

    @Test
    void evaluateWithHighSimilarity_returnsRejected() {
        // Given: high similarity (>= 0.85)
        Review newReview = createReview(1L, "Excellent product");
        Review existing = createReview(2L, "Excellent product");
        when(similarityCalculator.similarity(anyString(), anyString())).thenReturn(0.95);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(existing));

        // Then
        assertEquals(ReviewStatus.REJECTED, result.getStatus());
        assertEquals(0.95, result.getSimilarityScore(), 0.001);
        assertEquals(2L, result.getMostSimilarReviewId());
        assertTrue(result.getEvaluationReason().contains("Duplicate"));
    }

    @Test
    void evaluateFindsHighestSimilarity() {
        // Given: multiple reviews with different similarities
        Review newReview = createReview(1L, "Test review");
        Review review1 = createReview(2L, "Different");
        Review review2 = createReview(3L, "Somewhat similar");
        Review review3 = createReview(4L, "Very similar");

        when(similarityCalculator.similarity(newReview.getComment(), review1.getComment()))
                .thenReturn(0.2);
        when(similarityCalculator.similarity(newReview.getComment(), review2.getComment()))
                .thenReturn(0.5);
        when(similarityCalculator.similarity(newReview.getComment(), review3.getComment()))
                .thenReturn(0.8);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(review1, review2, review3));

        // Then
        assertEquals(0.8, result.getSimilarityScore(), 0.001);
        assertEquals(4L, result.getMostSimilarReviewId());
        assertEquals(ReviewStatus.FOR_MODERATION, result.getStatus());
    }

    @Test
    void evaluateRespectsMaxComparisons() {
        // Given: more reviews than max comparisons
        when(config.getMaxComparisons()).thenReturn(2);
        Review newReview = createReview(1L, "Test");
        List<Review> existing = new ArrayList<>();
        for (long i = 2; i <= 10; i++) {
            existing.add(createReview(i, "Review " + i));
        }
        when(similarityCalculator.similarity(anyString(), anyString())).thenReturn(0.3);

        // When
        EvaluationResult result = service.evaluate(newReview, existing);

        // Then: should only compare against first 2 reviews
        verify(similarityCalculator, times(2)).similarity(anyString(), anyString());
    }

    @Test
    void evaluateSkipsReviewsWithNullComments() {
        // Given: some reviews have null comments
        Review newReview = createReview(1L, "Test review");
        Review review1 = createReview(2L, null);
        Review review2 = createReview(3L, "Valid comment");

        when(similarityCalculator.similarity(anyString(), eq("Valid comment")))
                .thenReturn(0.5);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(review1, review2));

        // Then: should only calculate similarity for review2
        verify(similarityCalculator, times(1)).similarity(anyString(), anyString());
        assertEquals(0.5, result.getSimilarityScore(), 0.001);
    }

    @Test
    void evaluateHandlesCalculatorException_defaultsToModeration() {
        // Given: calculator throws exception
        Review newReview = createReview(1L, "Test");
        Review existing = createReview(2L, "Other");
        when(similarityCalculator.similarity(anyString(), anyString()))
                .thenThrow(new RuntimeException("Calculation error"));

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(existing));

        // Then: should default to FOR_MODERATION and not throw
        assertNotNull(result);
        assertEquals(ReviewStatus.FOR_MODERATION, result.getStatus());
        assertTrue(result.getEvaluationReason().contains("failed"));
    }

    @Test
    void determineStatusWithBoundaryValues() {
        // Test exact threshold boundaries
        assertEquals(ReviewStatus.APPROVED, service.determineStatus(0.59));
        assertEquals(ReviewStatus.FOR_MODERATION, service.determineStatus(0.60));
        assertEquals(ReviewStatus.FOR_MODERATION, service.determineStatus(0.84));
        assertEquals(ReviewStatus.REJECTED, service.determineStatus(0.85));
        assertEquals(ReviewStatus.REJECTED, service.determineStatus(1.0));
    }

    @Test
    void evaluatePopulatesAllResultFields() {
        // Given
        Review newReview = createReview(1L, "Test review");
        newReview.setProductId(100L);
        Review existing = createReview(2L, "Similar");
        when(similarityCalculator.similarity(anyString(), anyString())).thenReturn(0.7);

        // When
        EvaluationResult result = service.evaluate(newReview, List.of(existing));

        // Then: all fields should be populated
        assertEquals(1L, result.getReviewId()); // review ID is set in test helper
        assertEquals(100L, result.getProductId());
        assertEquals(0.7, result.getSimilarityScore(), 0.001);
        assertEquals(2L, result.getMostSimilarReviewId());
        assertNotNull(result.getEvaluationReason());
        assertNotNull(result.getEvaluatedAt());
        assertEquals(ReviewStatus.FOR_MODERATION, result.getStatus());
    }

    // Helper methods

    private Review createReview(Long id, String comment) {
        Review review = new Review();
        review.setId(id);
        review.setProductId(1L);
        review.setComment(comment);
        review.setRating(5);
        return review;
    }
}
