package nik.kalomiris.events.dtos.payment;

import java.math.BigDecimal;

/**
 * Base payment event payload shared by payment lifecycle events.
 * This class serves as the foundation for all payment-related domain events in the payment service.
 * It encapsulates essential payment information: order identifier, provider-specific intent ID, and amount.
 * All fields are immutable and validated at construction time to ensure data integrity.
 */
public class PaymentBaseEvent {

    private final String orderId;
    private final String providerIntentId;
    private final BigDecimal amount;

    /**
     * Creates a payment event with all fields set to null.
     * This no-args constructor is provided for JSON deserialization purposes and creates
     * an uninitialized payment event. Direct instantiation of this constructor should be avoided
     * in production code; use the parameterized constructor instead to ensure proper validation.
     * Use this constructor only when deserializing from external sources or for testing scenarios.
     */
    public PaymentBaseEvent() {
        this.orderId = null;
        this.providerIntentId = null;
        this.amount = null;
    }

    /**
     * Creates a payment event with validated required fields.
     * This constructor enforces strict validation of all parameters to ensure only valid
     * payment events are created. The amount must be positive, and all identifiers must be
     * non-null and non-empty.
     * @param orderId order identifier associated with the payment, must not be null
     * @param providerIntentId provider-specific payment intent identifier, must not be null
     * @param amount payment amount, must be greater than zero
     * @throws IllegalArgumentException if orderId is null, providerIntentId is null, or amount is invalid
     */
    public PaymentBaseEvent(String orderId, String providerIntentId, BigDecimal amount) {
        if (isAmountInvalid(amount)) {
            throw new IllegalArgumentException("Amount must be positive and not null.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OrderId must not be null.");
        }
        if (providerIntentId == null) {
            throw new IllegalArgumentException("ProviderIntentId must not be null.");
        }
        this.orderId = orderId;
        this.providerIntentId = providerIntentId;
        this.amount = amount;
    }

    /**
     * Returns the order identifier associated with this payment event.
     * This identifier uniquely identifies the order that triggered this payment event
     * and is used for correlating payment lifecycle events across the system.
     * @return the order identifier, never null
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Returns the provider-specific payment intent identifier.
     * This identifier is assigned by the external payment provider (e.g., Stripe) and
     * uniquely identifies the payment intent within the provider's system.
     * @return the provider-specific payment intent identifier, never null
     */
    public String getProviderIntentId() {
        return providerIntentId;
    }

    /**
     * Returns the payment amount for this event.
     * The amount is always positive and represents the monetary value being processed
     * in the payment transaction. The scale and currency are context-dependent.
     * @return the payment amount, always positive and never null
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Validates whether the provided amount is invalid for a payment event.
     * An amount is considered invalid if it is null or not positive (zero or negative).
     * This validation ensures that only valid monetary amounts are accepted.
     * @param amount the amount to validate, may be null
     * @return {@code true} if amount is null or less than or equal to zero, {@code false} otherwise
     */
    private boolean isAmountInvalid(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
    }

}
