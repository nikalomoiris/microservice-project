package nik.kalomiris.payment_service.domain;

public enum PaymentStatus {

    CREATED,
    AUTH_PENDING,
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    VOIDED,
    FAILED

}
