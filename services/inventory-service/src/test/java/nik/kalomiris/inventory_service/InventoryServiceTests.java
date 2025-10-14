package nik.kalomiris.inventory_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import nik.kalomiris.inventory_service.exceptions.InsufficientStockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
public class InventoryServiceTests {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        inventory = new Inventory("TEST-SKU", 100, 10);
        inventory = inventoryRepository.save(inventory);
    }

    @Test
    void reserveStock_shouldIncreaseReservedQuantity_whenStockIsAvailable() {
        // Act
        inventoryService.reserveStock(inventory.getId(), 20);

        // Assert
        Inventory updatedInventory = inventoryRepository.findById(inventory.getId()).get();
        assertThat(updatedInventory.getReservedQuantity()).isEqualTo(30);
        assertThat(updatedInventory.getQuantity()).isEqualTo(100);
    }

    @Test
    void reserveStock_shouldThrowException_whenStockIsNotAvailable() {
        // Assert
        assertThrows(InsufficientStockException.class, () -> {
            // Act
            inventoryService.reserveStock(inventory.getId(), 100); // Available is 90
        });
    }

    @Test
    void releaseStock_shouldDecreaseReservedQuantity() {
        // Act
        inventoryService.releaseStock(inventory.getId(), 5);

        // Assert
        Inventory updatedInventory = inventoryRepository.findById(inventory.getId()).get();
        assertThat(updatedInventory.getReservedQuantity()).isEqualTo(5);
    }

    @Test
    void releaseStock_shouldThrowException_whenReleasingMoreThanReserved() {
        // Assert
        assertThrows(InsufficientStockException.class, () -> {
            // Act
            inventoryService.releaseStock(inventory.getId(), 15); // Only 10 are reserved
        });
    }

    @Test
    void commitStock_shouldDecreaseQuantityAndReservedQuantity() {
        // Act
        inventoryService.commitStock(inventory.getId(), 5);

        // Assert
        Inventory updatedInventory = inventoryRepository.findById(inventory.getId()).get();
        assertThat(updatedInventory.getQuantity()).isEqualTo(95);
        assertThat(updatedInventory.getReservedQuantity()).isEqualTo(5);
    }

    @Test
    void commitStock_shouldThrowException_whenCommittingMoreThanReserved() {
        // Assert
        assertThrows(InsufficientStockException.class, () -> {
            // Act
            inventoryService.commitStock(inventory.getId(), 15); // Only 10 are reserved
        });
    }

    @Test
    void getInventoryBySku_shouldReturnDto_whenInventoryExists() {
        // Act
        var result = inventoryService.getInventoryBySku("TEST-SKU");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("TEST-SKU");
        assertThat(result.get().getQuantity()).isEqualTo(100);
        assertThat(result.get().getReservedQuantity()).isEqualTo(10);
        assertThat(result.get().isInStock()).isTrue();
    }
}