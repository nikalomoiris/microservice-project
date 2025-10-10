
package nik.kalomiris.inventory_service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
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
}
