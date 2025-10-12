
package nik.kalomiris.inventory_service;

import org.springframework.stereotype.Service;

import nik.kalomiris.inventory_service.exceptions.InsufficientStockException;
import nik.kalomiris.inventory_service.exceptions.InventoryNotFoundException;

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
            Inventory newInventory = new Inventory(sku, 0, 0); // Default to 0 quantity
            inventoryRepository.save(newInventory);
        }
    }

    public void reserveStock(Long productId, Integer amountToReserver) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getQuantity() - inventory.getReservedQuantity() >= amountToReserver) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() + amountToReserver);
                inventoryRepository.save(inventory);
                
            }
            else { 
                throw new InsufficientStockException("Not enough stock to reserve"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void releaseStock(Long productId, Integer amountToRelease) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToRelease) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToRelease);
                inventoryRepository.save(inventory);
            }
            else { 
                throw new InsufficientStockException("Not enough reserved stock to release"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }

    public void commitStock(Long productId, Integer amountToCommit) {
        Optional<Inventory> inventoryOpt = inventoryRepository.findById(productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            if (inventory.getReservedQuantity() >= amountToCommit) {
                inventory.setReservedQuantity(inventory.getReservedQuantity() - amountToCommit);
                inventory.setQuantity(inventory.getQuantity() - amountToCommit);
                inventoryRepository.save(inventory);
            }
            else { 
                throw new InsufficientStockException("Not enough reserved stock to commit"); 
            }
        }
        else {
            throw new InventoryNotFoundException("Inventory record not found for product ID: " + productId);
        }
    }
}
