package nik.kalomiris.review_service.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ReviewRepository using H2 in-memory database.
 * Tests all custom query methods and JPA repository behavior.
 */
@DataJpaTest
@ActiveProfiles("test")
class ReviewRepositoryTests {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Review review1;
    private Review review2;
    private Review review3;
    private Review review4;

    @BeforeEach
    void setUp() {
        // Create test reviews with different statuses and products
        review1 = createReview(1L, ReviewStatus.APPROVED, "Great product!", 5);
        review2 = createReview(1L, ReviewStatus.FOR_MODERATION, "Good product", 4);
        review3 = createReview(1L, ReviewStatus.REJECTED, "Spam review", 5);
        review4 = createReview(2L, ReviewStatus.APPROVED, "Different product review", 5);

        // Persist all reviews
        entityManager.persist(review1);
        entityManager.persist(review2);
        entityManager.persist(review3);
        entityManager.persist(review4);
        entityManager.flush();
    }

    @Test
    void findByProductId_returnsAllReviewsForProduct() {
        // When: find all reviews for product 1
        List<Review> reviews = reviewRepository.findByProductId(1L);

        // Then: should return 3 reviews (all statuses)
        assertEquals(3, reviews.size());
        assertTrue(reviews.stream().allMatch(r -> r.getProductId().equals(1L)));
    }

    @Test
    void findByProductId_returnsEmptyListForNonexistentProduct() {
        // When: find reviews for non-existent product
        List<Review> reviews = reviewRepository.findByProductId(999L);

        // Then: should return empty list
        assertTrue(reviews.isEmpty());
    }

    @Test
    void findByProductIdAndStatus_returnsOnlyApprovedReviews() {
        // When: find approved reviews for product 1
        List<Review> reviews = reviewRepository.findByProductIdAndStatus(1L, ReviewStatus.APPROVED);

        // Then: should return only approved reviews
        assertEquals(1, reviews.size());
        assertEquals(ReviewStatus.APPROVED, reviews.get(0).getStatus());
        assertEquals(1L, reviews.get(0).getProductId());
    }

    @Test
    void findByProductIdAndStatus_returnsOnlyModerationReviews() {
        // When: find moderation reviews for product 1
        List<Review> reviews = reviewRepository.findByProductIdAndStatus(1L, ReviewStatus.FOR_MODERATION);

        // Then: should return only moderation reviews
        assertEquals(1, reviews.size());
        assertEquals(ReviewStatus.FOR_MODERATION, reviews.get(0).getStatus());
    }

    @Test
    void findByProductIdAndStatus_returnsEmptyForNoMatches() {
        // When: find approved reviews for product with no approved reviews
        List<Review> reviews = reviewRepository.findByProductIdAndStatus(2L, ReviewStatus.REJECTED);

        // Then: should return empty list
        assertTrue(reviews.isEmpty());
    }

    @Test
    void findByStatus_returnsAllReviewsWithStatus() {
        // When: find all approved reviews across all products
        List<Review> approvedReviews = reviewRepository.findByStatus(ReviewStatus.APPROVED);

        // Then: should return 2 approved reviews (from product 1 and 2)
        assertEquals(2, approvedReviews.size());
        assertTrue(approvedReviews.stream().allMatch(r -> r.getStatus() == ReviewStatus.APPROVED));
    }

    @Test
    void findByStatus_returnsAllRejectedReviews() {
        // When: find all rejected reviews
        List<Review> rejectedReviews = reviewRepository.findByStatus(ReviewStatus.REJECTED);

        // Then: should return 1 rejected review
        assertEquals(1, rejectedReviews.size());
        assertEquals(ReviewStatus.REJECTED, rejectedReviews.get(0).getStatus());
    }

    @Test
    void findByProductIdAndIdNot_excludesSpecifiedReview() {
        // When: find reviews for product 1 excluding review1
        List<Review> reviews = reviewRepository.findByProductIdAndIdNot(1L, review1.getId());

        // Then: should return 2 reviews (review2 and review3)
        assertEquals(2, reviews.size());
        assertFalse(reviews.stream().anyMatch(r -> r.getId().equals(review1.getId())));
        assertTrue(reviews.stream().allMatch(r -> r.getProductId().equals(1L)));
    }

    @Test
    void findByProductIdAndIdNot_returnsAllReviewsIfExcludedIdNotFound() {
        // When: find reviews excluding non-existent ID
        List<Review> reviews = reviewRepository.findByProductIdAndIdNot(1L, 999L);

        // Then: should return all 3 reviews for product 1
        assertEquals(3, reviews.size());
    }

    @Test
    void saveReview_persistsAllFields() {
        // Given: a new review with all evaluation metadata
        Review newReview = new Review();
        newReview.setProductId(3L);
        newReview.setRating(4);
        newReview.setComment("Test review with metadata");
        newReview.setStatus(ReviewStatus.FOR_MODERATION);
        newReview.setSimilarityScore(0.75);
        newReview.setMostSimilarReviewId(1L);
        newReview.setEvaluationReason("Similar to existing review");
        newReview.setEvaluatedAt(Instant.now());
        newReview.setUpvotes(0);
        newReview.setDownvotes(0);

        // When: saving the review
        Review saved = reviewRepository.save(newReview);
        entityManager.flush();
        entityManager.clear();

        // Then: all fields should be persisted
        Review found = reviewRepository.findById(saved.getId()).orElseThrow();
        assertEquals(3L, found.getProductId());
        assertEquals(4, found.getRating());
        assertEquals("Test review with metadata", found.getComment());
        assertEquals(ReviewStatus.FOR_MODERATION, found.getStatus());
        assertEquals(0.75, found.getSimilarityScore(), 0.001);
        assertEquals(1L, found.getMostSimilarReviewId());
        assertEquals("Similar to existing review", found.getEvaluationReason());
        assertNotNull(found.getEvaluatedAt());
    }

    @Test
    void updateReview_modifiesExistingEntity() {
        // Given: an existing review
        Review existing = reviewRepository.findById(review1.getId()).orElseThrow();
        
        // When: updating status and moderation fields
        existing.setStatus(ReviewStatus.APPROVED);
        existing.setModeratedBy("admin@example.com");
        existing.setModeratedAt(Instant.now());
        reviewRepository.save(existing);
        entityManager.flush();
        entityManager.clear();

        // Then: changes should be persisted
        Review updated = reviewRepository.findById(review1.getId()).orElseThrow();
        assertEquals(ReviewStatus.APPROVED, updated.getStatus());
        assertEquals("admin@example.com", updated.getModeratedBy());
        assertNotNull(updated.getModeratedAt());
    }

    @Test
    void deleteReview_removesFromDatabase() {
        // Given: an existing review
        Long reviewId = review3.getId();
        assertTrue(reviewRepository.existsById(reviewId));

        // When: deleting the review
        reviewRepository.deleteById(reviewId);
        entityManager.flush();

        // Then: review should no longer exist
        assertFalse(reviewRepository.existsById(reviewId));
    }

    @Test
    void upvoteAndDownvote_persistCorrectly() {
        // Given: a review
        Review review = reviewRepository.findById(review1.getId()).orElseThrow();
        
        // When: updating votes
        review.setUpvotes(10);
        review.setDownvotes(2);
        reviewRepository.save(review);
        entityManager.flush();
        entityManager.clear();

        // Then: votes should be persisted
        Review updated = reviewRepository.findById(review1.getId()).orElseThrow();
        assertEquals(10, updated.getUpvotes());
        assertEquals(2, updated.getDownvotes());
    }

    /**
     * Helper method to create a test Review entity.
     */
    private Review createReview(Long productId, ReviewStatus status, String comment, int rating) {
        Review review = new Review();
        review.setProductId(productId);
        review.setComment(comment);
        review.setRating(rating);
        review.setStatus(status);
        review.setUpvotes(0);
        review.setDownvotes(0);
        return review;
    }
}
