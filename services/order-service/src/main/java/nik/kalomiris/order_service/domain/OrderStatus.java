package nik.kalomiris.order_service.domain;

public enum OrderStatus {
    CREATED,
    RESERVED,
    PARTIALLY_RESERVED,
    RESERVATION_FAILED,
    CANCELLED,
    SHIPPED,
    COMPLETED
}
