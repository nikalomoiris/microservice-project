
package nik.kalomiris.inventory_service;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySku(String sku);
    Optional<Inventory> findByProductId(Long productId);
}
