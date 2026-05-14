package nik.kalomiris.events.dtos.payment;

import java.math.BigDecimal;

/**
 * Event published after a payment provider confirms that funds were captured.
 *
 * <p>This event is used by downstream services to mark an order as paid and
 * continue post-payment workflows.</p>
 */
public class PaymentCapturedEvent extends PaymentBaseEvent {

    /**
     * No-args constructor for serialization frameworks.
     */
    private PaymentCapturedEvent() {
        super();
    }

    /**
     * Creates a payment captured event.
     *
     * @param orderId the order identifier for the captured payment
     * @param providerIntentId the provider payment intent/transaction identifier
     * @param amount the amount captured from the customer
     */
    public PaymentCapturedEvent(String orderId, String providerIntentId, BigDecimal amount) {
        super(orderId, providerIntentId, amount);
    }
}
