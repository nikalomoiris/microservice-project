
package nik.kalomiris.inventory_service;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository for Inventory entities.
 *
 * Provides simple finder methods used by the service and event listeners.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    /** Find inventory by SKU (unique per product catalog). */
    Optional<Inventory> findBySku(String sku);

    /** Find inventory by productId (maps to product-service identifier). */
    Optional<Inventory> findByProductId(Long productId);
}
