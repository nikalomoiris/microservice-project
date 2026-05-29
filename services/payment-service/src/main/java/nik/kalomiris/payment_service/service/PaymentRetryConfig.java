package nik.kalomiris.payment_service.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import nik.kalomiris.payment_service.provider.dto.FailureType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.validation.annotation.Validated;

/**
 * Binds and applies retry settings for payment provider calls.
 */
@Configuration
@ConfigurationProperties(prefix = "payment.retry")
@Validated
public class PaymentRetryConfig {

	public static final int DEFAULT_MAX_ATTEMPTS = 3;
	public static final long DEFAULT_INITIAL_DELAY_MS = 1_000L;
	public static final double DEFAULT_MULTIPLIER = 2.0d;
	public static final long DEFAULT_MAX_BACKOFF_MS = 10_000L;

	@Min(1)
	private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

	@Min(1)
	private long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;

	@DecimalMin("1.0")
	private double multiplier = DEFAULT_MULTIPLIER;

	@Min(1)
	private long maxBackoffMs = DEFAULT_MAX_BACKOFF_MS;

	@Bean
	public RetryTemplate paymentRetryTemplate() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));

		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
		backOffPolicy.setInitialInterval(initialDelayMs);
		backOffPolicy.setMultiplier(multiplier);
		backOffPolicy.setMaxInterval(Math.max(initialDelayMs, maxBackoffMs));

		retryTemplate.setBackOffPolicy(backOffPolicy);
		retryTemplate.setThrowLastExceptionOnExhausted(true);
		return retryTemplate;
	}

	public long calculateBackoffDelayMs(int attemptNumber) {
		if (attemptNumber <= 1) {
			return initialDelayMs;
		}
		double exponentialDelay = initialDelayMs * Math.pow(multiplier, attemptNumber - 1L);
		long roundedDelay = Math.round(exponentialDelay);
		return Math.clamp(roundedDelay, initialDelayMs, Math.max(initialDelayMs, maxBackoffMs));
	}

	public boolean isRetryableFailure(FailureType failureType) {
		return failureType == FailureType.TRANSIENT;
	}

	public boolean hasAttemptsRemaining(int attemptsAlreadyUsed) {
		return attemptsAlreadyUsed < maxAttempts;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public long getInitialDelayMs() {
		return initialDelayMs;
	}

	public void setInitialDelayMs(long initialDelayMs) {
		this.initialDelayMs = initialDelayMs;
	}

	public double getMultiplier() {
		return multiplier;
	}

	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
	}

	public long getMaxBackoffMs() {
		return maxBackoffMs;
	}

	public void setMaxBackoffMs(long maxBackoffMs) {
		this.maxBackoffMs = maxBackoffMs;
	}
}
