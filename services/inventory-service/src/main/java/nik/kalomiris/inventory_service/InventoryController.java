
package nik.kalomiris.inventory_service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/inventory")
/**
 * HTTP REST controller exposing inventory operations used by clients and tests.
 *
 * Keep controllers thin: delegate to {@link InventoryService} for business logic.
 */
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryDTO> getInventoryBySku(@PathVariable String sku) {
        Optional<InventoryDTO> inventoryOpt = inventoryService.getInventoryBySku(sku);
        return inventoryOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{productId}/reserver")
    public ResponseEntity<Void> reserveStock(@PathVariable Long productId, @RequestBody Integer quantityToReserve) {
        inventoryService.reserveStock(productId, quantityToReserve);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{productId}/release")
    public ResponseEntity<Void> releaseStock(@PathVariable Long productId, @RequestBody Integer quantityToRelease) {
        inventoryService.releaseStock(productId, quantityToRelease);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{productId}/quantity")
    public ResponseEntity<Void> setQuantity(@PathVariable Long productId, @RequestBody Integer quantity) {
        inventoryService.setQuantity(productId, quantity);
        return ResponseEntity.ok().build();
    }

    // Alternate endpoint that accepts quantity via path variable to avoid any
    // request-body mapping issues from clients/tests.
    @PostMapping("/{productId}/quantity/{quantity}")
    public ResponseEntity<Void> setQuantityPath(@PathVariable Long productId, @PathVariable Integer quantity) {
        inventoryService.setQuantity(productId, quantity);
        return ResponseEntity.ok().build();
    }

    // Test/admin helper: create inventory record if missing (idempotent)
    @PostMapping("/{productId}/create")
    public ResponseEntity<Void> createInventoryIfMissing(@PathVariable Long productId, @org.springframework.web.bind.annotation.RequestParam String sku) {
        inventoryService.createInventoryRecord(productId, sku);
        return ResponseEntity.ok().build();
    }
    
    
}
