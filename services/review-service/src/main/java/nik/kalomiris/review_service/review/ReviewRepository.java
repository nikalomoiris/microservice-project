package nik.kalomiris.review_service.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    /**
     * Repository for Review entities. Provides finder methods used by
     * controllers and services.
     */
    List<Review> findByProductId(Long productId);
}
