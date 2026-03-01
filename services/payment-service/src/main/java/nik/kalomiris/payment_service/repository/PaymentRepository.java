package nik.kalomiris.payment_service.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import nik.kalomiris.payment_service.domain.Payment;
import nik.kalomiris.payment_service.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByStatusAndRetryCountLessThan(PaymentStatus status, int maxRetryCount);

    Optional<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime cutoffTime);

}
