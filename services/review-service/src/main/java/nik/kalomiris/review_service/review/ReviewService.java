package nik.kalomiris.review_service.review;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.review_service.evaluation.ReviewEvaluationService;
import nik.kalomiris.review_service.similarity.EvaluationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final ReviewRepository reviewRepository;
    private final LogPublisher logPublisher;
    private final ReviewEvaluationService evaluationService;

    public ReviewService(ReviewRepository reviewRepository, LogPublisher logPublisher,
            ReviewEvaluationService evaluationService) {
        this.reviewRepository = reviewRepository;
        this.logPublisher = logPublisher;
        this.evaluationService = evaluationService;
    }

    public Review createReview(Review review) {
        // Save review first to get ID (required for evaluation result)
        Review savedReview = reviewRepository.save(review);

        // Fetch existing reviews for the same product to compare against (excluding
        // this one)
        List<Review> existingReviews = reviewRepository.findByProductId(review.getProductId())
                .stream()
                .filter(r -> !r.getId().equals(savedReview.getId()))
                .toList();

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

    public Review addUpVote(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
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
                .orElseThrow(() -> new RuntimeException("Review not found with id: " + reviewId));
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
