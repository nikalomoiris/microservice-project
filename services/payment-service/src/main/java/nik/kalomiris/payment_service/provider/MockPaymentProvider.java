package nik.kalomiris.payment_service.provider;

import java.math.BigDecimal;

import nik.kalomiris.payment_service.provider.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import nik.kalomiris.payment_service.domain.Payment;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "payment.provider", havingValue = "mock")
@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final String INTENT_ID_PREFIX = "mock_intent_";
    private static final String AUTH_FAILED_MESSAGE = "Authorization failed - last digit ";
    private static final String AUTH_SUCCESS_MESSAGE = "Authorization successful - last digit ";

    private static final String CAPTURE_ID_PREFIX = "mock_capture_";
    private static final String CAPTURE_FAILED_MESSAGE = "Capture failed - last digit ";
    private static final String CAPTURE_SUCCESS_MESSAGE = "Capture successful - last digit ";

    private static final String REFUND_ID_PREFIX = "mock_refund_";
    private static final String REFUND_FAILED_MESSAGE = "Refund failed - last digit ";
    private static final String REFUND_SUCCESS_MESSAGE = "Refund successful - last digit ";

    private static final String VOID_FAILED_MESSAGE = "Void failed - last digit ";
    private static final String VOID_SUCCESS_MESSAGE = "Void successful - last digit ";

    private static final String TIMEOUT_ERROR_CODE = "TIMEOUT";
    private static final String PROVIDER_UNAVAILABLE_ERROR_CODE = "PROVIDER_UNAVAILABLE";

    private char getLastDigit(BigDecimal amount) {
        String plainStr = amount.toPlainString();
        String digits = plainStr.replace(".", "");
        return digits.charAt(digits.length() - 1);
    }

    @Override
    public ProviderAuthResult authorize(Payment payment) {
        char lastDigit = getLastDigit(payment.getAmount());

        return switch (lastDigit) {
            case '0', '1', '2', '3' -> ProviderAuthResult.success()
                    .withIntentId(INTENT_ID_PREFIX + payment.getId())
                    .withMessage(AUTH_SUCCESS_MESSAGE + lastDigit)
                    .build();
            case '4', '5' -> ProviderAuthResult.failure()
                    .withErrorCode("CARD_DECLINED")
                    .withFailureType(FailureType.PERMANENT)
                    .withMessage(AUTH_FAILED_MESSAGE + lastDigit)
                    .build();
            case '6', '7' -> ProviderAuthResult.failure()
                    .withErrorCode(TIMEOUT_ERROR_CODE)
                    .withFailureType(FailureType.TRANSIENT)
                    .withMessage(AUTH_FAILED_MESSAGE + lastDigit)
                    .build();
            case '8', '9' -> ProviderAuthResult.failure()
                    .withErrorCode(PROVIDER_UNAVAILABLE_ERROR_CODE)
                    .withFailureType(FailureType.TRANSIENT)
                    .withMessage(AUTH_FAILED_MESSAGE + lastDigit)
                    .build();
            default -> throw new IllegalStateException("Unexpected last digit: " + lastDigit);
        };
    }

    @Override
    public ProviderCaptureResult capture(Payment payment) {

        char lastDigit = getLastDigit(payment.getAmount());

        switch (lastDigit) {
            case '0', '1', '2', '3':
                return ProviderCaptureResult.success()
                        .withCaptureId(CAPTURE_ID_PREFIX + payment.getId())
                        .withMessage(CAPTURE_SUCCESS_MESSAGE + lastDigit)
                        .build();

            case '4', '5':
                return ProviderCaptureResult.failure()
                        .withErrorCode("CAPTURE_DECLINED")
                        .withFailureType(FailureType.PERMANENT)
                        .withMessage(CAPTURE_FAILED_MESSAGE + lastDigit)
                        .build();

            case '6', '7':
                return ProviderCaptureResult.failure()
                        .withErrorCode(TIMEOUT_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(CAPTURE_FAILED_MESSAGE + lastDigit)
                        .build();

            case '8', '9':
                return ProviderCaptureResult.failure()
                        .withErrorCode(PROVIDER_UNAVAILABLE_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(CAPTURE_FAILED_MESSAGE + lastDigit)
                        .build();

            default:
                throw new IllegalStateException("Unexpected last digit: " + lastDigit);
        }

    }

    @Override
    public ProviderRefundResult refund(Payment payment, BigDecimal amount) {

        char lastDigit = getLastDigit(amount);

        switch (lastDigit) {
            case '0', '1', '2', '3':
                return ProviderRefundResult.success()
                        .withRefundId(REFUND_ID_PREFIX + payment.getId())
                        .withMessage(REFUND_SUCCESS_MESSAGE + lastDigit)
                        .build();

            case '4', '5':
                return ProviderRefundResult.failure()
                        .withErrorCode("REFUND_DECLINED")
                        .withFailureType(FailureType.PERMANENT)
                        .withMessage(REFUND_FAILED_MESSAGE + lastDigit)
                        .build();

            case '6', '7':
                return ProviderRefundResult.failure()
                        .withErrorCode(TIMEOUT_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(REFUND_FAILED_MESSAGE + lastDigit)
                        .build();

            case '8', '9':
                return ProviderRefundResult.failure()
                        .withErrorCode(PROVIDER_UNAVAILABLE_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(REFUND_FAILED_MESSAGE + lastDigit)
                        .build();

            default:
                throw new IllegalStateException("Unexpected last digit: " + lastDigit);
        }

    }

    @Override
    public ProviderVoidResult voidPayment(Payment payment) {

        char lastDigit = getLastDigit(payment.getAmount());

        switch (lastDigit) {
            case '0', '1', '2', '3':
                return ProviderVoidResult.success()
                        .withMessage(VOID_SUCCESS_MESSAGE + lastDigit)
                        .build();

            case '4', '5':
                return ProviderVoidResult.failure()
                        .withErrorCode("VOID_DECLINED")
                        .withFailureType(FailureType.PERMANENT)
                        .withMessage(VOID_FAILED_MESSAGE + lastDigit)
                        .build();

            case '6', '7':
                return ProviderVoidResult.failure()
                        .withErrorCode(TIMEOUT_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(VOID_FAILED_MESSAGE + lastDigit)
                        .build();

            case '8', '9':
                return ProviderVoidResult.failure()
                        .withErrorCode(PROVIDER_UNAVAILABLE_ERROR_CODE)
                        .withFailureType(FailureType.TRANSIENT)
                        .withMessage(VOID_FAILED_MESSAGE + lastDigit)
                        .build();

            default:
                throw new IllegalStateException("Unexpected last digit: " + lastDigit);
        }

    }

}
