package nik.kalomiris.review_service.review;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.review_service.evaluation.ReviewEvaluationService;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReviewService {

    /**
     * Service managing review lifecycle: creation, voting and querying.
     * Publishes lightweight structured log messages for observability but
     * treats logging failures as non-fatal.
     */

    private static final String SERVICE_NAME = "review-service";
    private static final String REVIEW_SERVICE_LOGGER = "nik.kalomiris.review_service.review.ReviewService";
    private static final String REVIEW_ID = "reviewId";
    private static final String PRODUCT_ID = "productId";
    private static final String REVIEW_NOT_FOUND = "Review not found with id: ";

    private final ReviewRepository reviewRepository;
    private final LogPublisher logPublisher;
    private final ReviewEvaluationService evaluationService;
    private final nik.kalomiris.review_service.metrics.ReviewMetrics reviewMetrics;

    @Autowired
    public ReviewService(ReviewRepository reviewRepository, LogPublisher logPublisher,
            ReviewEvaluationService evaluationService,
            nik.kalomiris.review_service.metrics.ReviewMetrics reviewMetrics) {
        this.reviewRepository = reviewRepository;
        this.logPublisher = logPublisher;
        this.evaluationService = evaluationService;
        this.reviewMetrics = reviewMetrics;
    }

    // Backward-compatible constructor for tests without metrics
    public ReviewService(ReviewRepository reviewRepository, LogPublisher logPublisher,
            ReviewEvaluationService evaluationService) {
        this(reviewRepository, logPublisher, evaluationService, null);
    }

    public Review createReview(Review review) {
        // Save review first to get ID (required for evaluation result)
        Review savedReview = reviewRepository.save(review);

        // Fetch existing reviews for the same product to compare against (excluding
        // this one)
        List<Review> existingReviews = reviewRepository.findByProductIdAndIdNot(
                savedReview.getProductId(),
                savedReview.getId());

        // Evaluate the saved review against existing ones
        EvaluationResult evaluationResult = evaluationService.evaluate(savedReview, existingReviews);

        // Apply evaluation result to review entity and update
        savedReview.setStatus(evaluationResult.getStatus());
        savedReview.setSimilarityScore(evaluationResult.getSimilarityScore());
        savedReview.setMostSimilarReviewId(evaluationResult.getMostSimilarReviewId());
        savedReview.setEvaluationReason(evaluationResult.getEvaluationReason());
        savedReview.setEvaluatedAt(evaluationResult.getEvaluatedAt());

        Review finalReview = reviewRepository.save(savedReview);

        // Publish a log event about the review creation. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review created")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(REVIEW_SERVICE_LOGGER)
                    .metadata(Map.of(
                            REVIEW_ID, finalReview.getId().toString(),
                            PRODUCT_ID, finalReview.getProductId().toString(),
                            "status", finalReview.getStatus().toString(),
                            "similarityScore",
                            evaluationResult.getSimilarityScore() != null
                                    ? evaluationResult.getSimilarityScore().toString()
                                    : "null"))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        // Metrics: mark review creation
        try {
            if (reviewMetrics != null) {
                reviewMetrics.markReviewAdded();
            }
        } catch (Exception ignored) {
            /* best-effort */ }
        return finalReview;
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }

    public List<Review> getReviewsByProductId(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public List<Review> getReviewsByProductIdAndStatus(Long productId, ReviewStatus status) {
        if (status == null) {
            // Default to APPROVED only for backward compatibility
            return reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED);
        }
        return reviewRepository.findByProductIdAndStatus(productId, status);
    }

    public List<Review> getReviewsByStatus(ReviewStatus status) {
        return reviewRepository.findByStatus(status);
    }

    public Review updateReviewStatus(Long reviewId, ReviewStatus newStatus, String moderatorId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException(REVIEW_NOT_FOUND + reviewId));

        ReviewStatus oldStatus = review.getStatus();
        review.setStatus(newStatus);
        review.setModeratedBy(moderatorId);
        review.setModeratedAt(java.time.Instant.now());

        Review updatedReview = reviewRepository.save(review);

        // Log status change
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review status updated")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(REVIEW_SERVICE_LOGGER)
                    .metadata(Map.of(
                            REVIEW_ID, reviewId.toString(),
                            PRODUCT_ID, review.getProductId().toString(),
                            "oldStatus", oldStatus.toString(),
                            "newStatus", newStatus.toString(),
                            "moderatedBy", moderatorId))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return updatedReview;
    }

    public Review addUpVote(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException(REVIEW_NOT_FOUND + reviewId));
        if (review.getUpvotes() == null) {
            review.setUpvotes(0);
        }
        review.setUpvotes(review.getUpvotes() + 1);
        Review updatedReview = reviewRepository.save(review);

        // Publish a log event about the upvote. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review upvoted")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(REVIEW_SERVICE_LOGGER)
                    .metadata(Map.of(REVIEW_ID, reviewId.toString(), PRODUCT_ID, review.getProductId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return updatedReview;
    }

    public Review addDownVote(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException(REVIEW_NOT_FOUND + reviewId));
        if (review.getDownvotes() == null) {
            review.setDownvotes(0);
        }
        review.setDownvotes(review.getDownvotes() + 1);
        Review updatedReview = reviewRepository.save(review);

        // Publish a log event about the downvote. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review downvoted")
                    .level("INFO")
                    .service(SERVICE_NAME)
                    .logger(REVIEW_SERVICE_LOGGER)
                    .metadata(Map.of(REVIEW_ID, reviewId.toString(), PRODUCT_ID, review.getProductId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return updatedReview;
    }
}
