
package nik.kalomiris.inventory_service;

import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryDTO toDto(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return new InventoryDTO(
            inventory.getSku(),
            inventory.getQuantity(),
            inventory.getReservedQuantity(),
            inventory.getQuantity() > 0
        );
    }

    public Inventory toEntity(InventoryDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Inventory(
            dto.getSku(),
            dto.getQuantity(),
            dto.getReservedQuantity()
        );
    }
}
