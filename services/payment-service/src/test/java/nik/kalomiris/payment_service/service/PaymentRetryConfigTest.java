package nik.kalomiris.payment_service.service;

import nik.kalomiris.payment_service.provider.dto.FailureType;
import nik.kalomiris.payment_service.PaymentServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRetryConfigTest {

    @Test
    void defaultsMatchDocumentation() {
        PaymentRetryConfig config = new PaymentRetryConfig();

        assertEquals(3, config.getMaxAttempts());
        assertEquals(1_000L, config.getInitialDelayMs());
        assertEquals(2.0d, config.getMultiplier(), 0.0001d);
        assertEquals(10_000L, config.getMaxBackoffMs());
    }

    @Test
    void bindsKebabCaseProperties() {
        PaymentRetryConfig config = new PaymentRetryConfig();
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "payment.retry.max-attempts", "5",
                "payment.retry.initial-delay-ms", "250",
                "payment.retry.multiplier", "1.5",
                "payment.retry.max-backoff-ms", "2000"
        ));

        new Binder(source).bind("payment.retry", Bindable.ofInstance(config));

        assertEquals(5, config.getMaxAttempts());
        assertEquals(250L, config.getInitialDelayMs());
        assertEquals(1.5d, config.getMultiplier(), 0.0001d);
        assertEquals(2_000L, config.getMaxBackoffMs());
    }

    @Test
    void helperMethodsClassifyFailuresAndAttempts() {
        PaymentRetryConfig config = new PaymentRetryConfig();

        assertTrue(config.isRetryableFailure(FailureType.TRANSIENT));
        assertFalse(config.isRetryableFailure(FailureType.PERMANENT));
        assertFalse(config.isRetryableFailure(null));

        assertTrue(config.hasAttemptsRemaining(0));
        assertTrue(config.hasAttemptsRemaining(2));
        assertFalse(config.hasAttemptsRemaining(3));
    }

    @Test
    void calculatesExponentialBackoffWithCap() {
        PaymentRetryConfig config = new PaymentRetryConfig();
        config.setInitialDelayMs(100);
        config.setMultiplier(2.0d);
        config.setMaxBackoffMs(250);

        assertEquals(100L, config.calculateBackoffDelayMs(1));
        assertEquals(200L, config.calculateBackoffDelayMs(2));
        assertEquals(250L, config.calculateBackoffDelayMs(3));
    }

    @Test
    void retryTemplateUsesConfiguredAttemptCount() {
        PaymentRetryConfig config = new PaymentRetryConfig();
        config.setMaxAttempts(3);
        config.setInitialDelayMs(1);
        config.setMultiplier(1.1d);
        config.setMaxBackoffMs(1);

        RetryTemplate retryTemplate = config.paymentRetryTemplate();
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> retryTemplate.execute(context -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fail");
        }));
        assertEquals(3, attempts.get());
    }

    @Test
    void applicationEnablesScheduling() {
        assertTrue(PaymentServiceApplication.class.isAnnotationPresent(EnableScheduling.class));
    }
}