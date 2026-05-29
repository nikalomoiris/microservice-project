package nik.kalomiris.payment_service.provider.dto;

/**
 * Categorizes provider failures by retry behavior.
 */
public enum FailureType {
    TRANSIENT,
    PERMANENT
}
