package nik.kalomiris.order_service.domain;

public enum OrderStatus {
    CREATED,
    RESERVED,
    PARTIALLY_RESERVED,
    CONFIRMED,
    COMMITTED,
    SHIPPED,
    COMPLETED,
    RESERVATION_FAILED,
    CANCELLED
}
