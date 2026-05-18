package nik.kalomiris.event_contracts.dtos.payment;

import java.math.BigDecimal;

/**
 * Event published after a payment provider authorizes payment for an order.
 *
 * <p>Contains the order identifier, provider intent identifier, and the authorized amount.
 * Consumers can use this event to continue order fulfillment after payment is secured.
 *
 * @see PaymentBaseEvent
 */
public class PaymentAuthorizedEvent extends PaymentBaseEvent {

    private PaymentAuthorizedEvent() {
        super();
    }

    /**
     * Creates a {@code PaymentAuthorizedEvent} with payment authorization details.
     *
     * @param orderId unique identifier of the order associated with this payment
     * @param providerIntentId payment provider authorization/intent identifier
     * @param amount amount authorized by the payment provider
     */
    public PaymentAuthorizedEvent(String orderId, String providerIntentId, BigDecimal amount) {
        super(orderId, providerIntentId, amount);
    }
}
