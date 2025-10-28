
package nik.kalomiris.inventory_service;

import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    /**
     * Simple mapper between entity and DTO used by REST endpoints and tests.
     * Keep mapping logic straightforward so the domain and transport models
     * remain decoupled.
     */

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
