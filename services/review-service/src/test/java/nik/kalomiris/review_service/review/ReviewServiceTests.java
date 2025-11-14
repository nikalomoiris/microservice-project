package nik.kalomiris.review_service.review;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.review_service.evaluation.ReviewEvaluationService;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService focusing on review creation and evaluation
 * integration.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTests {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private LogPublisher logPublisher;

    @Mock
    private ReviewEvaluationService evaluationService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, logPublisher, evaluationService);
    }

    @Test
    void createReviewWithNoExistingReviewsShouldBeApproved() {
        // Given: new review for a product with no existing reviews
        Review newReview = createReview(null, 1L, "Great product!");

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(1L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.0)
                .mostSimilarReviewId(null)
                .evaluationReason("No existing reviews to compare")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.APPROVED)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(Collections.emptyList());
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: review should be evaluated and approved
        assertNotNull(result);
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.0, result.getSimilarityScore());
        assertNull(result.getMostSimilarReviewId());
        assertNotNull(result.getEvaluationReason());
        assertNotNull(result.getEvaluatedAt());

        verify(reviewRepository).findByProductId(1L);
        verify(evaluationService).evaluate(any(Review.class), anyList());
        verify(reviewRepository).save(newReview);
    }

    @Test
    void createReviewWithLowSimilarityShouldBeApproved() {
        // Given: new review similar to existing but below moderation threshold
        Review existingReview = createReview(1L, 1L, "Good product, works well");
        Review newReview = createReview(null, 1L, "Excellent quality!");

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(2L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.45)
                .mostSimilarReviewId(1L)
                .evaluationReason("Similarity below threshold (0.45 < 0.60)")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.APPROVED)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(List.of(existingReview));
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: review should be approved
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.45, result.getSimilarityScore());
        assertEquals(1L, result.getMostSimilarReviewId());

        verify(evaluationService).evaluate(any(Review.class), anyList());
    }

    @Test
    void createReviewWithModerateSimilarityShouldBeQueuedForModeration() {
        // Given: new review moderately similar to existing
        Review existingReview = createReview(1L, 1L, "Great product, highly recommend");
        Review newReview = createReview(null, 1L, "Great product, I recommend it");

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(2L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.72)
                .mostSimilarReviewId(1L)
                .evaluationReason("Similarity requires moderation (0.72 >= 0.60)")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.FOR_MODERATION)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(List.of(existingReview));
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: review should be queued for moderation
        assertEquals(ReviewStatus.FOR_MODERATION, result.getStatus());
        assertEquals(0.72, result.getSimilarityScore());
        assertEquals(1L, result.getMostSimilarReviewId());

        verify(evaluationService).evaluate(any(Review.class), anyList());
    }

    @Test
    void createReviewWithHighSimilarityShouldBeRejected() {
        // Given: new review very similar to existing (likely duplicate/spam)
        Review existingReview = createReview(1L, 1L, "Amazing product, best purchase ever!");
        Review newReview = createReview(null, 1L, "Amazing product, best purchase ever!");

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(2L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.98)
                .mostSimilarReviewId(1L)
                .evaluationReason("High similarity detected (0.98 >= 0.85) - potential duplicate")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.REJECTED)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(List.of(existingReview));
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: review should be rejected
        assertEquals(ReviewStatus.REJECTED, result.getStatus());
        assertEquals(0.98, result.getSimilarityScore());
        assertEquals(1L, result.getMostSimilarReviewId());
        assertTrue(result.getEvaluationReason().contains("duplicate"));

        verify(evaluationService).evaluate(any(Review.class), anyList());
    }

    @Test
    void createReviewComparesAgainstMultipleExistingReviews() {
        // Given: product has multiple reviews
        Review review1 = createReview(1L, 1L, "Good product");
        Review review2 = createReview(2L, 1L, "Nice quality");
        Review review3 = createReview(3L, 1L, "Works well");
        Review newReview = createReview(null, 1L, "Very good product");

        List<Review> existingReviews = Arrays.asList(review1, review2, review3);

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(4L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.55)
                .mostSimilarReviewId(1L)
                .evaluationReason("Highest similarity: 0.55 with review #1")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.APPROVED)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(existingReviews);
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: should evaluate against all existing reviews
        verify(reviewRepository).findByProductId(1L);
        verify(evaluationService).evaluate(any(Review.class), argThat(list -> list != null && list.size() == 3));
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
    }

    @Test
    void createReviewPopulatesAllEvaluationMetadata() {
        // Given: new review
        Review newReview = createReview(null, 1L, "Test review");
        Instant evaluatedAt = Instant.now();

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(1L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.42)
                .mostSimilarReviewId(10L)
                .evaluationReason("Test evaluation reason")
                .evaluatedAt(evaluatedAt)
                .status(ReviewStatus.APPROVED)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(Collections.emptyList());
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When: creating the review
        Review result = reviewService.createReview(newReview);

        // Then: all evaluation metadata should be populated
        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals(0.42, result.getSimilarityScore());
        assertEquals(10L, result.getMostSimilarReviewId());
        assertEquals("Test evaluation reason", result.getEvaluationReason());
        assertEquals(evaluatedAt, result.getEvaluatedAt());
    }

    @Test
    void createReviewLogsStatusAndSimilarityScore() {
        // Given: new review
        Review newReview = createReview(null, 1L, "Test review");

        EvaluationResult evaluationResult = EvaluationResult.builder()
                .reviewId(1L) // ID that will be assigned after save
                .productId(1L)
                .similarityScore(0.75)
                .mostSimilarReviewId(null)
                .evaluationReason("Test")
                .evaluatedAt(Instant.now())
                .status(ReviewStatus.FOR_MODERATION)
                .build();

        when(reviewRepository.findByProductId(1L)).thenReturn(Collections.emptyList());
        when(evaluationService.evaluate(any(Review.class), anyList())).thenReturn(evaluationResult);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When: creating the review
        reviewService.createReview(newReview);

        // Then: should attempt to log with status and similarity score
        verify(logPublisher).publish(any(LogMessage.class));
    }

    /**
     * Helper method to create a Review instance for testing.
     */
    private Review createReview(Long id, Long productId, String comment) {
        Review review = new Review();
        review.setId(id);
        review.setProductId(productId);
        review.setComment(comment);
        review.setRating(5);
        review.setStatus(ReviewStatus.APPROVED);
        return review;
    }
}
