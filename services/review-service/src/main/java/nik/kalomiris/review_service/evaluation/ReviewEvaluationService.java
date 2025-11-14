package nik.kalomiris.review_service.evaluation;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.review_service.config.ReviewEvaluationConfig;
import nik.kalomiris.review_service.review.Review;
import nik.kalomiris.review_service.review.ReviewStatus;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import nik.kalomiris.review_service.similarity.SimilarityCalculator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for evaluating new reviews for similarity against
 * existing reviews.
 * 
 * This service orchestrates the review evaluation workflow:
 * 1. Compares new review against existing reviews for the same product
 * 2. Calculates similarity scores using configured algorithm
 * 3. Determines review status based on configurable thresholds
 * 4. Builds evaluation result with metadata for audit trail
 * 
 * Thread-safe and suitable for concurrent use.
 */
@Service
public class ReviewEvaluationService {

    private static final String SERVICE_NAME = "review-service";
    private static final String LOGGER_NAME = "nik.kalomiris.review_service.evaluation.ReviewEvaluationService";
    private static final String PRODUCT_ID = "productId";

    private final SimilarityCalculator similarityCalculator;
    private final ReviewEvaluationConfig config;
    private final LogPublisher logPublisher;

    public ReviewEvaluationService(
            SimilarityCalculator similarityCalculator,
            ReviewEvaluationConfig config,
            LogPublisher logPublisher) {
        this.similarityCalculator = similarityCalculator;
        this.config = config;
        this.logPublisher = logPublisher;
    }

    /**
     * Evaluate a new review against existing reviews for the same product.
     * 
     * Algorithm:
     * 1. If evaluation is disabled via feature flag, return APPROVED
     * 2. If no existing reviews, return APPROVED (first review for product)
     * 3. Compare new review against up to maxComparisons existing reviews
     * 4. Find highest similarity score
     * 5. Determine status based on thresholds
     * 6. Build and return evaluation result
     * 
     * @param newReview       the review to evaluate (not yet persisted)
     * @param existingReviews existing reviews for the same product
     * @return evaluation result with status, score, and metadata
     */
    public EvaluationResult evaluate(Review newReview, List<Review> existingReviews) {
        try {
            // Feature flag check - if disabled, auto-approve
            if (!config.isEnabled()) {
                logEvaluationSkipped(newReview.getProductId(), "evaluation disabled");
                return buildResult(newReview, ReviewStatus.APPROVED, 0.0, null,
                        "Evaluation disabled via feature flag");
            }

            // If no existing reviews, auto-approve (first review for product)
            if (existingReviews == null || existingReviews.isEmpty()) {
                logEvaluationComplete(newReview.getProductId(), ReviewStatus.APPROVED, 0.0);
                return buildResult(newReview, ReviewStatus.APPROVED, 0.0, null,
                        "First review for product");
            }

            // Limit comparisons to configured maximum
            int comparisonLimit = Math.min(existingReviews.size(), config.getMaxComparisons());
            List<Review> reviewsToCompare = existingReviews.subList(0, comparisonLimit);

            // Find highest similarity score
            double maxSimilarity = 0.0;
            Review mostSimilarReview = null;

            for (Review existing : reviewsToCompare) {
                if (existing.getComment() == null || existing.getComment().isEmpty()) {
                    continue; // Skip reviews without comments
                }

                double similarity = similarityCalculator.similarity(
                        newReview.getComment(),
                        existing.getComment());

                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    mostSimilarReview = existing;
                }
            }

            // Determine status based on thresholds
            ReviewStatus status = determineStatus(maxSimilarity);
            String reason = buildEvaluationReason(status, maxSimilarity, reviewsToCompare.size());

            logEvaluationComplete(newReview.getProductId(), status, maxSimilarity);

            return buildResult(
                    newReview,
                    status,
                    maxSimilarity,
                    mostSimilarReview != null ? mostSimilarReview.getId() : null,
                    reason);

        } catch (Exception e) {
            // Graceful degradation: on evaluation failure, default to FOR_MODERATION
            logEvaluationFailure(newReview.getProductId(), e);
            return buildResult(newReview, ReviewStatus.FOR_MODERATION, null, null,
                    "Evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Determine review status based on similarity score and configured thresholds.
     * 
     * Thresholds:
     * - score < approved threshold → APPROVED
     * - approved ≤ score < moderation threshold → FOR_MODERATION
     * - score ≥ moderation threshold → REJECTED
     * 
     * @param similarityScore highest similarity score (0.0 - 1.0)
     * @return determined review status
     */
    ReviewStatus determineStatus(double similarityScore) {
        double approvedThreshold = config.getThreshold().getApproved();
        double moderationThreshold = config.getThreshold().getModeration();

        if (similarityScore < approvedThreshold) {
            return ReviewStatus.APPROVED;
        } else if (similarityScore < moderationThreshold) {
            return ReviewStatus.FOR_MODERATION;
        } else {
            return ReviewStatus.REJECTED;
        }
    }

    /**
     * Build human-readable evaluation reason for audit trail.
     */
    private String buildEvaluationReason(ReviewStatus status, double similarity, int comparedCount) {
        String scoreFormatted = String.format("%.2f", similarity);
        return switch (status) {
            case APPROVED -> String.format("Unique content (similarity: %s, compared: %d)",
                    scoreFormatted, comparedCount);
            case FOR_MODERATION -> String.format("Similar content detected (similarity: %s, compared: %d)",
                    scoreFormatted, comparedCount);
            case REJECTED -> String.format("Duplicate detected (similarity: %s, compared: %d)",
                    scoreFormatted, comparedCount);
        };
    }

    /**
     * Build evaluation result with all metadata populated.
     */
    private EvaluationResult buildResult(
            Review review,
            ReviewStatus status,
            Double similarityScore,
            Long mostSimilarReviewId,
            String reason) {
        return EvaluationResult.builder()
                .reviewId(review.getId())
                .productId(review.getProductId())
                .similarityScore(similarityScore != null ? similarityScore : 0.0)
                .mostSimilarReviewId(mostSimilarReviewId)
                .evaluationReason(reason)
                .evaluatedAt(Instant.now())
                .status(status)
                .build();
    }

    // Logging helpers

    private void logEvaluationSkipped(Long productId, String reason) {
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review evaluation skipped")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(
                            PRODUCT_ID, productId.toString(),
                            "reason", reason))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // Ignore logging failures
        }
    }

    private void logEvaluationComplete(Long productId, ReviewStatus status, double similarity) {
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review evaluation complete")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(
                            PRODUCT_ID, productId.toString(),
                            "status", status.name(),
                            "similarityScore", String.format("%.3f", similarity)))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // Ignore logging failures
        }
    }

    private void logEvaluationFailure(Long productId, Exception error) {
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review evaluation failed")
                    .level("ERROR")
                    .service(SERVICE_NAME)
                    .logger(LOGGER_NAME)
                    .metadata(Map.of(
                            PRODUCT_ID, productId.toString(),
                            "error", error.getMessage()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // Ignore logging failures
        }
    }
}
