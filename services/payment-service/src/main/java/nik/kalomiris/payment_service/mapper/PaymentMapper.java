package nik.kalomiris.payment_service.mapper;

import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.dto.PaymentRequest;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public Payment toPayment(PaymentRequest paymentRequest) {
        return new Payment.Builder()
                .withOrderId(paymentRequest.getOrderId())
                .withAmount(paymentRequest.getAmount())
                .build();
    }
}
