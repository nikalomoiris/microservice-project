package nik.kalomiris.review_service.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    /**
     * Repository for Review entities. Provides finder methods used by
     * controllers and services.
     */
    List<Review> findByProductId(Long productId);

    /**
     * Find reviews by product ID and status.
     * 
     * @param productId the product ID
     * @param status    the review status
     * @return list of reviews matching criteria
     */
    List<Review> findByProductIdAndStatus(Long productId, ReviewStatus status);

    /**
     * Find reviews by status across all products.
     * 
     * @param status the review status
     * @return list of reviews with the given status
     */
    List<Review> findByStatus(ReviewStatus status);

    /**
     * Find reviews by product ID excluding a specific review (used for evaluation).
     * 
     * @param productId the product ID
     * @param id        the review ID to exclude
     * @return list of reviews for the product excluding the specified one
     */
    List<Review> findByProductIdAndIdNot(Long productId, Long id);
}
