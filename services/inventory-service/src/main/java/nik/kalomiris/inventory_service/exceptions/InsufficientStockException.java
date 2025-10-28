package nik.kalomiris.inventory_service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientStockException extends RuntimeException {
    /**
     * Thrown when there is not enough stock to satisfy a reserve/release/commit
     * operation. Mapped to HTTP 409 CONFLICT for REST clients.
     */
    public InsufficientStockException(String message) {
        super(message);
    }
}