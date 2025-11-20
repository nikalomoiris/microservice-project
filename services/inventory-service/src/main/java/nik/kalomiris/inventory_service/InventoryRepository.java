
package nik.kalomiris.inventory_service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

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

    @Query("SELECT SUM(i.quantity) FROM Inventory i")
    Long sumTotalQuantity();

    @Query("SELECT SUM(i.reservedQuantity) FROM Inventory i")
    Long sumReservedQuantity();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity < :threshold AND i.quantity > 0")
    long countLowStock(@Param("threshold") int threshold);

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity = 0")
    long countOutOfStock();
}
