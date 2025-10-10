
package nik.kalomiris.inventory_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InventoryServiceTests {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getInventoryBySku_shouldReturnDto_whenInventoryExists() {
        // Arrange
        String sku = "TEST-SKU";
        Inventory inventory = new Inventory(sku, 10);
        InventoryDTO inventoryDTO = new InventoryDTO(sku, 10, true);

        when(inventoryRepository.findBySku(sku)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toDto(inventory)).thenReturn(inventoryDTO);

        // Act
        Optional<InventoryDTO> result = inventoryService.getInventoryBySku(sku);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo(sku);
        assertThat(result.get().getQuantity()).isEqualTo(10);
        assertThat(result.get().isInStock()).isTrue();
    }

    @Test
    void getInventoryBySku_shouldReturnEmpty_whenInventoryDoesNotExist() {
        // Arrange
        String sku = "NON-EXISTENT-SKU";
        when(inventoryRepository.findBySku(sku)).thenReturn(Optional.empty());

        // Act
        Optional<InventoryDTO> result = inventoryService.getInventoryBySku(sku);

        // Assert
        assertThat(result).isNotPresent();
    }
}
