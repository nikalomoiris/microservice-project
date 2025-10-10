
package nik.kalomiris.inventory_service;

import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryRepository inventoryRepository, InventoryMapper inventoryMapper) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
    }

    public Optional<InventoryDTO> getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(inventoryMapper::toDto);
    }

    public void createInventoryRecord(String sku) {
        // Check if inventory for this SKU already exists
        if (inventoryRepository.findBySku(sku).isEmpty()) {
            Inventory newInventory = new Inventory(sku, 0); // Default to 0 quantity
            inventoryRepository.save(newInventory);
        }
    }
}
