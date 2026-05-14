package nik.kalomiris.events.dtos.payment;

import java.math.BigDecimal;

/**
 * Event published when payment authorization fails for an order.
 */
public class PaymentAuthorizationFailedEvent extends PaymentBaseEvent {

    private final String errorMessage;

    private PaymentAuthorizationFailedEvent() {
        super();
        this.errorMessage = null;
    }

    /**
     * Creates a payment authorization failed event.
     *
     * @param orderId order identifier associated with the payment attempt
     * @param providerIntentId provider-specific intent identifier
     * @param amount amount that failed authorization
     * @param errorMessage provider or business error details for the failed authorization
     * @throws IllegalArgumentException if {@code errorMessage} is {@code null}
     */
    public PaymentAuthorizationFailedEvent(String orderId, String providerIntentId, BigDecimal amount, String errorMessage) {
        super(orderId, providerIntentId, amount);
        if (errorMessage == null) {
            throw new IllegalArgumentException("errorMessage must not be null");
        }
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the reason payment authorization failed.
     *
     * @return non-null payment authorization failure message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
