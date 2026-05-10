package nik.kalomiris.payment_service.service;

import jakarta.transaction.Transactional;
import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.domain.PaymentStatus;
import nik.kalomiris.payment_service.provider.PaymentProvider;
import nik.kalomiris.payment_service.provider.dto.ProviderAuthResult;
import nik.kalomiris.payment_service.repository.PaymentRepository;
import nik.kalomiris.payment_service.util.Logger;
import nik.kalomiris.payment_service.util.PaymentStatusTransitions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PaymentRecoveryJob {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final Logger logger;
    private final PaymentStatusTransitions paymentStatusTransitions;

    public PaymentRecoveryJob(PaymentRepository paymentRepository, PaymentProvider paymentProvider,
                              Logger logger,  PaymentStatusTransitions paymentStatusTransitions) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.logger = logger;
        this.paymentStatusTransitions = paymentStatusTransitions;
    }

    @Value("${payment.retry.max-attempts-recovery-job}")
    private int maxRetries;

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    public void retryPendingAuthorizations() {
        List<Payment> pendingPayments = paymentRepository
                .findByStatusAndRetryCountLessThan(PaymentStatus.AUTH_PENDING, maxRetries);

        for (Payment payment : pendingPayments) {
            try {
                ProviderAuthResult authResult = paymentProvider.authorize(payment);
                if (authResult.isSuccess()) {
                    if (paymentStatusTransitions.canTransitionTo(payment.getStatus(), PaymentStatus.AUTHORIZED)) {
                        payment.setStatus(PaymentStatus.AUTHORIZED);
                        payment.setProviderIntentId(authResult.getIntentId());
                        paymentRepository.save(payment);
                        logger.publishLog(this.getClass().getSimpleName(), "INFO", "Payment authorization successful", payment);
                    } else {
                        // If we can't transition to AUTHORIZED, we log it and skip updating the status
                        payment.setRetryCount(payment.getRetryCount() + 1);
                        paymentRepository.save(payment);
                        logger.publishLog(this.getClass().getSimpleName(), "ERROR", "Payment authorization successful but cannot transition from " + payment.getStatus() + " to AUTHORIZED", payment);
                    }
                }
            } catch (Exception e) {
                payment.setRetryCount(payment.getRetryCount() + 1);
                if (payment.getRetryCount() >= maxRetries) {
                    if (paymentStatusTransitions.canTransitionTo(payment.getStatus(), PaymentStatus.FAILED)) {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);
                        logger.publishLog(this.getClass().getSimpleName(), "WARN","Payment authorization not successful", payment);
                    } else {
                        // If we can't transition to FAILED, we log it and skip updating the status
                        paymentRepository.save(payment);
                        logger.publishLog(this.getClass().getSimpleName(), "ERROR", "Payment authorization failed and cannot transition from " + payment.getStatus() + " to FAILED", payment);
                    }
                } else {
                    paymentRepository.save(payment);
                    logger.publishLog(this.getClass().getSimpleName(), "WARN", "Payment authorization failed but retries remain", payment);
                }
            }
        }
    }
}
