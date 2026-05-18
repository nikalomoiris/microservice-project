package nik.kalomiris.event_contracts.routing;

public class PaymentExchangeContracts {

    public static final String EXCHANGE_NAME = "payment-exchange";

    public static final String PAYMENT_CREATED_QUEUE_NAME = "payment-created-queue";
    public static final String PAYMENT_AUTHORIZATION_QUEUE_NAME = "payment-authorization-queue";
    public static final String PAYMENT_CAPTURE_QUEUE_NAME = "payment-capture-queue";
    public static final String PAYMENT_REFUND_QUEUE_NAME = "payment-refund-queue";
    public static final String PAYMENT_VOID_QUEUE_NAME = "payment-void-queue";

    public static final String ROUTING_KEY_PAYMENT_CREATED = "payment.created";
    public static final String ROUTING_KEY_PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String ROUTING_KEY_PAYMENT_AUTHORIZATION_FAILED = "payment.authorization.failed";
    public static final String ROUTING_KEY_PAYMENT_CAPTURED = "payment.captured";
    public static final String ROUTING_KEY_PAYMENT_CAPTURE_FAILED = "payment.capture.failed";
    public static final String ROUTING_KEY_PAYMENT_REFUNDED = "payment.refunded";
    public static final String ROUTING_KEY_PAYMENT_REFUND_FAILED = "payment.refund.failed";
    public static final String ROUTING_KEY_PAYMENT_VOIDED = "payment.voided";

}
