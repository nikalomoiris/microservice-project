package nik.kalomiris.payment_service.service;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.dto.PaymentRequest;
import nik.kalomiris.payment_service.mapper.PaymentMapper;
import nik.kalomiris.payment_service.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final LogPublisher logPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper,
            LogPublisher logPublisher
    ){
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.logPublisher = logPublisher;
    }

    public void createPayment(PaymentRequest paymentRequest) {

        Optional<Payment> existingPayment = paymentRepository.findByOrderId(paymentRequest.getOrderId());
        if (existingPayment.isPresent()) {
            // Idempotency: if payment already exists for this order, ignore the request
            return;
        }

        Payment payment = paymentMapper.toPayment(paymentRequest);
        
        // Save the payment to the repository
        paymentRepository.save(payment);

        try {
            LogMessage logMessage = new LogMessage.Builder()
                    .service("PaymentService")
                    .level("INFO")
                    .message("Creating payment for order " + payment.getOrderId())
                    .logger("nik.kalomiris.payment_service.service.PaymentService")
                    .metadata(Map.of("orderId", payment.getOrderId()
                            ,"amount", payment.getAmount()
                            ,"paymentId", payment.getId()
                            ,"status", payment.getStatus().toString()
                            ,"retryCount", payment.getRetryCount()
                            ,"providerIntentId", payment.getProviderIntentId()
                            ,"currency", payment.getCurrency()))
                    .build();
            logPublisher.publish(logMessage);
        } catch (Exception e) {
            // Ignore logging failures
        }

    }
}
