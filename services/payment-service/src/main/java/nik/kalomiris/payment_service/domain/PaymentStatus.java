package nik.kalomiris.payment_service.domain;

/**
 * Lifecycle states for payment processing.
 */
public enum PaymentStatus {

    CREATED,
    AUTH_PENDING,
    AUTHORIZED,
    CAPTURE_PENDING,
    CAPTURED,
    REFUNDED,
    VOIDED,
    FAILED

}
