package nik.kalomiris.order_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.OptimisticLockException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class RetryUtilsTest {

    @Test
    void retrySucceedsAfterTransientOptimisticLock() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            if (counter.getAndIncrement() == 0) {
                throw new OptimisticLockException("simulated transient");
            }
            return "ok";
        };

        String result = RetryUtils.retryOnOptimisticLock(supplier, 3, 10);
        assertEquals("ok", result);
    }

    @Test
    void retryExhaustsAndThrows() {
        Supplier<Void> alwaysFail = () -> {
            throw new OptimisticLockException("always fail");
        };

        assertThrows(OptimisticLockException.class, () -> RetryUtils.retryOnOptimisticLock(alwaysFail, 2, 5));
    }
}
