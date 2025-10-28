package nik.kalomiris.inventory_service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class InventoryNotFoundException extends RuntimeException {
    /**
     * Thrown when a requested Inventory record cannot be found.
     *
     * Annotated with {@link ResponseStatus} so REST endpoints will return 404.
     */
    public InventoryNotFoundException(String message) {
        super(message);
    }
}