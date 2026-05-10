package nik.kalomiris.payment_service.service;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.domain.PaymentStatus;
import nik.kalomiris.payment_service.dto.PaymentRequest;
import nik.kalomiris.payment_service.mapper.PaymentMapper;
import nik.kalomiris.payment_service.provider.PaymentProvider;
import nik.kalomiris.payment_service.provider.dto.ProviderAuthResult;
import nik.kalomiris.payment_service.repository.PaymentRepository;
import nik.kalomiris.payment_service.util.Logger;
import nik.kalomiris.payment_service.util.PaymentStatusTransitions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RetryTemplate retryTemplate;
    private final PaymentProvider paymentProvider;
    private final PaymentStatusTransitions paymentStatusTransitions;
    private final Logger logger;
    @Value("${payment.retry.max-attempts}")
    private int maxRetries;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper,
            RetryTemplate retryTemplate,
            PaymentProvider paymentProvider,
            PaymentStatusTransitions paymentStatusTransitions,
            Logger logger
    ){
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.retryTemplate = retryTemplate;
        this.paymentProvider = paymentProvider;
        this.paymentStatusTransitions = paymentStatusTransitions;
        this.logger = logger;
    }

    //TODO Implement event publishing
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
            logger.publishLog(this.getClass().getSimpleName(), "INFO", "Creating payment for order " + payment.getOrderId(), payment);
        } catch (Exception e) {
            // Ignore logging failures
        }

        authorizePayment(payment);
    }

    private void authorizePayment(Payment payment) {
        try {
            ProviderAuthResult authResult = retryTemplate.execute(context -> {
                // Call the payment provider to authorize the payment
                return paymentProvider.authorize(payment);
            });

            if(authResult.isSuccess()){
                if (paymentStatusTransitions.canTransitionTo(payment.getStatus(), PaymentStatus.AUTHORIZED)) {
                    payment.setStatus(PaymentStatus.AUTHORIZED);
                    payment.setProviderIntentId(authResult.getIntentId());
                    paymentRepository.save(payment);
                }
            }

            try {
                logger.publishLog(this.getClass().getSimpleName(), "INFO", "Payment authorization successful", payment);
            } catch (Exception e) {
                // Ignore logging failures
            }

        } catch (Exception e) {
            // Handle authorization failures
            if (paymentStatusTransitions.canTransitionTo(payment.getStatus(), PaymentStatus.AUTH_PENDING)) {
                payment.setStatus(PaymentStatus.AUTH_PENDING);
                payment.setRetryCount(maxRetries);
                paymentRepository.save(payment);
            }

            try {
                logger.publishLog(this.getClass().getSimpleName(), "WARN", "Payment authorization not successful", payment);
            } catch (Exception exception) {
                // Ignore logging failures
            }
        }
    }
}
