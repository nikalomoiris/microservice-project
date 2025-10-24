package nik.kalomiris.review_service.review;

import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.logging_client.LogMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final LogPublisher logPublisher;

    public ReviewService(ReviewRepository reviewRepository, LogPublisher logPublisher) {
        this.reviewRepository = reviewRepository;
        this.logPublisher = logPublisher;
    }

    public Review createReview(Review review) {
        Review savedReview = reviewRepository.save(review);

        // Publish a log event about the review creation. Ignore logging failures.
        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .message("Review created")
                    .level("INFO")
                    .service("review-service")
                    .logger("nik.kalomiris.review_service.review.ReviewService")
                    .metadata(Map.of("reviewId", savedReview.getId().toString(), "productId", savedReview.getProductId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return savedReview;
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
                    .service("review-service")
                    .logger("nik.kalomiris.review_service.review.ReviewService")
                    .metadata(Map.of("reviewId", reviewId.toString(), "productId", review.getProductId().toString()))
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
                    .service("review-service")
                    .logger("nik.kalomiris.review_service.review.ReviewService")
                    .metadata(Map.of("reviewId", reviewId.toString(), "productId", review.getProductId().toString()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // ignore logging failures
        }

        return updatedReview;
    }
}
