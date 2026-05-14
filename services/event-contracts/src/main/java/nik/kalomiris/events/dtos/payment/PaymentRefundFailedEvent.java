package nik.kalomiris.events.dtos.payment;

import java.math.BigDecimal;

/**
 * Event representing a failed payment refund operation.
 * This event is published when a refund request fails, containing details
 * about the order, payment provider intent, refund amount, and error information.
 */
public class PaymentRefundFailedEvent extends PaymentBaseEvent {

    private final String errorMessage;
    private final BigDecimal refundAmount;

    private PaymentRefundFailedEvent() {
        super();
        this.errorMessage = null;
        this.refundAmount = null;
    }

    /**
     * Constructs a new PaymentRefundFailedEvent with specified details.
     *
     * @param orderId the unique identifier of the order
     * @param providerIntentId the payment provider's intent identifier
     * @param amount the original order amount
     * @param errorMessage the error message describing why the refund failed
     * @param refundAmount the amount that was attempted to be refunded and failed
     * @throws IllegalArgumentException if {@code errorMessage} or {@code refundAmount} is {@code null}
     */
    public PaymentRefundFailedEvent(String orderId, String providerIntentId, BigDecimal amount,
                                    String errorMessage, BigDecimal refundAmount) {
        super(orderId, providerIntentId, amount);
        if (errorMessage == null) {
            throw new IllegalArgumentException("Error message must not be null.");
        }
        if (refundAmount == null) {
            throw new IllegalArgumentException("Refund amount must not be null.");
        }
        this.errorMessage = errorMessage;
        this.refundAmount = refundAmount;
    }

    /**
     * Gets the error message describing why the refund failed.
     *
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the refund amount that failed to be processed.
     *
     * @return the refund amount associated with the failed refund operation
     */
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

}
