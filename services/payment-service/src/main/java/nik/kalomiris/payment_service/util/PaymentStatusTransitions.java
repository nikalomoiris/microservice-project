package nik.kalomiris.payment_service.util;

import nik.kalomiris.payment_service.domain.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Encapsulates allowed transitions between payment lifecycle states.
 */
@Component
public class PaymentStatusTransitions {

    public boolean canTransitionTo(PaymentStatus current, PaymentStatus target) {
        return switch (current) {
            case CREATED -> target == PaymentStatus.AUTHORIZED || target == PaymentStatus.AUTH_PENDING
                    || target == PaymentStatus.VOIDED;
            case AUTH_PENDING -> target == PaymentStatus.AUTHORIZED || target == PaymentStatus.VOIDED
                    || target == PaymentStatus.FAILED;
            case AUTHORIZED -> target == PaymentStatus.CAPTURE_PENDING || target == PaymentStatus.CAPTURED
                    || target == PaymentStatus.VOIDED;
            case CAPTURE_PENDING -> target == PaymentStatus.CAPTURED || target == PaymentStatus.VOIDED
                    || target == PaymentStatus.FAILED;
            case CAPTURED -> target == PaymentStatus.REFUNDED;
            // Terminal statuses VOIDED, FAILED, and REFUNDED cannot transition to any other status
            case VOIDED, FAILED, REFUNDED -> false;
        };
    }

}
