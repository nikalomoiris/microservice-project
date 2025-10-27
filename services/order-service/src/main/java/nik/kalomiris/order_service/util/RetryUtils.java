package nik.kalomiris.order_service.util;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.OptimisticLockException;

public class RetryUtils {
    private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

    public static <T> T retryOnOptimisticLock(Supplier<T> action, int maxAttempts, long backoffMs) {
        int attempts = 0;
        while (true) {
            try {
                return action.get();
            } catch (OptimisticLockException | org.hibernate.StaleObjectStateException e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    logger.warn("Max optimistic lock retry attempts ({}) reached", maxAttempts);
                    throw e;
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }
    
}
