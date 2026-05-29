package nik.kalomiris.payment_service.provider;

import java.math.BigDecimal;
import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.provider.dto.ProviderAuthResult;
import nik.kalomiris.payment_service.provider.dto.ProviderCaptureResult;
import nik.kalomiris.payment_service.provider.dto.ProviderRefundResult;
import nik.kalomiris.payment_service.provider.dto.ProviderVoidResult;

/**
 * Abstraction for external payment authorization and settlement operations.
 */
public interface PaymentProvider {
    ProviderAuthResult authorize(Payment payment);

    ProviderCaptureResult capture(Payment payment);

    ProviderRefundResult refund(Payment payment, BigDecimal amount);

    ProviderVoidResult voidPayment(Payment payment);
}
