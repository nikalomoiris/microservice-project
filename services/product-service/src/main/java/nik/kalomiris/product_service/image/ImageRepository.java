package nik.kalomiris.product_service.image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
	// Additional query methods (if needed) can be defined here
}

/**
 * Repository for Image entities. Define query methods here when needed
 * to look up images by product id or other attributes.
 */
