package nik.kalomiris.payment_service.util;

import nik.kalomiris.logging_client.LogMessage;
import nik.kalomiris.logging_client.LogPublisher;
import nik.kalomiris.payment_service.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes structured payment logs through the shared logging client.
 */
@Component
public class Logger {

    private final LogPublisher logPublisher;

    public Logger(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    public void publishLog(String className, String level, String message, Payment payment) {
        LogMessage logMessage = new LogMessage.Builder()
                .service(className)
                .level(level)
                .message(message)
                .logger("nik.kalomiris.payment_service.service." + className)
                .metadata(Map.of("orderId", payment.getOrderId()
                        ,"amount", payment.getAmount()
                        ,"paymentId", payment.getId()
                        ,"status",  payment.getStatus().toString()
                        ,"retryCount", payment.getRetryCount()
                        ,"providerIntentId", payment.getProviderIntentId()
                        ,"currency", payment.getCurrency()))
                .build();
        logPublisher.publish(logMessage);
    }

}
