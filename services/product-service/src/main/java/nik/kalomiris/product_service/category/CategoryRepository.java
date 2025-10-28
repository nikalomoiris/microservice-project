package nik.kalomiris.product_service.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    /**
     * Repository for Category entities. Provides basic CRUD and finder methods.
     */
    boolean existsByName(String name);
}
