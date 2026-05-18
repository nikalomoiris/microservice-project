package nik.kalomiris.event_contracts.dtos.payment;

import java.math.BigDecimal;

/**
 * Event emitted after a payment refund is successfully completed.
 */
public class PaymentRefundedEvent extends PaymentBaseEvent {

    private final BigDecimal amountRefunded;

    /**
     * No-args constructor for serialization frameworks.
     */
    private PaymentRefundedEvent() {
        super();
        this.amountRefunded = null;
    }

    /**
     * Creates a {@code PaymentRefundedEvent}.
     *
     * @param orderId order identifier associated with the payment
     * @param providerIntentId provider-specific payment intent identifier
     * @param amount original payment amount
     * @param amountRefunded refunded amount; must be positive and less than or equal to {@code amount}
     * @throws IllegalArgumentException if {@code amountRefunded} is {@code null}, non-positive, or greater than {@code amount}
     */
    public PaymentRefundedEvent(String orderId, String providerIntentId, BigDecimal amount, BigDecimal amountRefunded) {
        super(orderId, providerIntentId, amount);
        if (isRefundInvalid(amount, amountRefunded)) {
            throw new IllegalArgumentException("Amount refunded must not be null. Amount refunded must be positive and less than or equal to the original amount.");
        }
        this.amountRefunded = amountRefunded;
    }

    /**
     * Returns the refunded amount.
     *
     * @return refunded amount
     */
    public BigDecimal getAmountRefunded() {
        return amountRefunded;
    }

    private boolean isRefundInvalid(BigDecimal amount, BigDecimal amountRefunded) {
        return amountRefunded == null || amountRefunded.compareTo(BigDecimal.ZERO) <= 0
                || amountRefunded.compareTo(amount) > 0;
    }

}
