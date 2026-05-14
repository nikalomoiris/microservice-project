package nik.kalomiris.events.dtos.payment;

import java.math.BigDecimal;

/**
 * Event published when payment capture fails for an order.
 *
 * <p>Includes the failed capture amount and a human-readable error message
 * returned or derived from the payment flow.
 */
public class PaymentCaptureFailedEvent extends PaymentBaseEvent {

    private final String errorMessage;

    private PaymentCaptureFailedEvent() {
        super();
        this.errorMessage = null;
    }

    /**
     * Creates a payment-capture-failed event.
     *
     * @param orderId the order identifier
     * @param providerIntentId the external payment provider intent identifier
     * @param amount the capture amount that failed
     * @param errorMessage the failure reason; must not be {@code null}
     */
    public PaymentCaptureFailedEvent(String orderId, String providerIntentId, BigDecimal amount, String errorMessage) {
        super(orderId, providerIntentId, amount);
        if (errorMessage == null) {
            throw new IllegalArgumentException("errorMessage must not be null");
        }
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the capture failure reason.
     *
     * @return the failure message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
