# Payment Service — Quick Reference Guide

A one-page reference guide for implementation. Keep this open while coding.

---

## State Transitions Cheat Sheet

```
Event: order.created
├─ Check: SELECT * FROM payments WHERE order_id = ?
├─ If exists: SKIP (idempotent)
└─ If not: CREATE + AUTHORIZE
   ├─ Try authorize() with RetryTemplate (3x)
   ├─ Success → status=AUTHORIZED, publish payment.authorized
   ├─ Transient fail → status=AUTH_PENDING, retry_count=1
   └─ Non-transient fail → status=FAILED, publish payment.authorization_failed

Event: order.confirmed
├─ Check: SELECT * FROM payments WHERE order_id = ? AND status='AUTHORIZED'
├─ If exists + AUTHORIZED: CAPTURE
│  ├─ Try capture() with RetryTemplate (3x)
│  ├─ Success → status=CAPTURED, publish payment.captured
│  └─ Fail → status=AUTH_PENDING, retry_count++
└─ If already CAPTURED: SKIP (idempotent)

Event: order.cancelled
├─ Check: SELECT * FROM payments WHERE order_id = ?
├─ If AUTHORIZED: VOID
│  ├─ Call provider.void()
│  ├─ Success → status=VOIDED, publish payment.voided
│  └─ Fail → retry? (low priority)
├─ If CREATED/AUTH_PENDING: Mark VOIDED (no provider call)
└─ If CAPTURED or beyond: Consider refund (future)

Background Job (every 5 min):
├─ Query: SELECT * FROM payments WHERE status='AUTH_PENDING' AND retry_count < 10
├─ For each: Try authorize/capture again
├─ If success: Update status, publish event
├─ If fail: retry_count++
└─ If retry_count >= 10: status=FAILED, publish failure event
```

---

## Code Templates

### Idempotency Check
```java
Optional<Payment> existing = paymentRepository.findByOrderId(event.getOrderId());
if (existing.isPresent()) {
    logger.info("Payment already exists for order {}, skipping", event.getOrderId());
    return; // STOP HERE - don't process duplicate
}
// Continue with normal flow...
```

### RetryTemplate Usage
```java
try {
    ProviderAuthResult result = retryTemplate.execute(context -> {
        logger.info("Authorization attempt #{}", context.getRetryCount() + 1);
        return paymentProvider.authorize(payment);
    });
    
    if (result.isSuccess()) {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setProviderIntentId(result.getIntentId());
    }
} catch (Exception e) {
    // Retries exhausted - mark for background job
    payment.setStatus(PaymentStatus.AUTH_PENDING);
    payment.setRetryCount(3); // All retries used
    logger.error("Authorization retries exhausted, marking AUTH_PENDING", e);
}
paymentRepository.save(payment);
```

### Event Publishing (Post-Commit)
```java
@Transactional
public void authorizePayment(Payment payment) {
    // ... authorization logic ...
    payment.setStatus(PaymentStatus.AUTHORIZED);
    paymentRepository.save(payment); // Saves to DB, transaction not committed yet
    
    // Register callback to run AFTER transaction commits
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Now safe to publish - DB change is permanent
                rabbitTemplate.convertAndSend(
                    PAYMENT_EXCHANGE,
                    "payment.authorized",
                    new PaymentAuthorizedEvent(payment.getId(), payment.getOrderId(), payment.getStatus())
                );
            }
        }
    );
}
```

### Structured Logging
```java
LogMessage log = new LogMessage.Builder()
    .message("Payment authorization started")
    .level("INFO")
    .service("payment-service")
    .metadata(Map.of(
        "paymentId", payment.getId().toString(),
        "orderId", payment.getOrderId().toString(),
        "amount", payment.getAmount().toString(),
        "status", payment.getStatus().toString()
    ))
    .build();
logPublisher.publish(log);
```

### Background Job Pattern
```java
@Component
public class PaymentRecoveryJob {
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void retryPendingAuthorizations() {
        List<Payment> pending = paymentRepository.findByStatusAndRetryCountLessThan(
            PaymentStatus.AUTH_PENDING, 
            10 // max retries
        );
        
        for (Payment payment : pending) {
            try {
                ProviderAuthResult result = paymentProvider.authorize(payment);
                if (result.isSuccess()) {
                    payment.setStatus(PaymentStatus.AUTHORIZED);
                    publishEvent(new PaymentAuthorizedEvent(...));
                }
            } catch (Exception e) {
                payment.setRetryCount(payment.getRetryCount() + 1);
                if (payment.getRetryCount() >= 10) {
                    payment.setStatus(PaymentStatus.FAILED);
                    publishEvent(new PaymentAuthorizationFailedEvent(...));
                }
            }
            paymentRepository.save(payment);
        }
    }
}
```

---

## Key Validations

| Field | Validation |
|-------|-----------|
| `orderId` | NOT NULL, UUID format |
| `amount` | NOT NULL, @Positive, max 999,999.99 |
| `currency` | NOT NULL, 3 uppercase letters (regex: `[A-Z]{3}`) |
| `paymentId` | UUID format for path parameters |
| Refund amount | <= captured_total - refunded_total |

---

## Query Patterns

```java
// Idempotency check
paymentRepository.findByOrderId(orderId)

// For capture operation
paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.AUTHORIZED)

// For background job
paymentRepository.findByStatusAndRetryCountLessThan(PaymentStatus.AUTH_PENDING, 10)

// For cleanup job
paymentRepository.findByStatusAndCreatedAtBefore(
    PaymentStatus.FAILED, 
    LocalDateTime.now().minus(365, ChronoUnit.DAYS)
)
```

---

## Common Mistakes to Avoid

❌ **Publishing events BEFORE saving to DB**
- Event consumer might try to look up payment, can't find it
- Fix: Use `TransactionSynchronization` to publish after commit

❌ **Retrying non-transient failures**
- Card declined will never succeed on retry
- Fix: Check exception type, only retry network/timeout errors

❌ **Forgetting idempotency check**
- Duplicate events cause double-processing
- Fix: Always check for existing resource FIRST

❌ **Confusing retry with recovery**
- Retry = immediate retries in listener
- Recovery = background job for stuck payments
- Fix: Keep them separate

❌ **Storing full payment details**
- PCI compliance issue
- Fix: Store only provider intent ID and last4 (future)

❌ **Not handling optimistic lock failures**
- Concurrent updates can fail
- Fix: Use `RetryUtils.retryOnOptimisticLock()`

---

## Testing Checklist

- [ ] Unit: State transitions (CREATED → AUTH_PENDING → AUTHORIZED)
- [ ] Unit: Retry behavior (success on 2nd attempt)
- [ ] Unit: Idempotency (duplicate event returns early)
- [ ] Integration: Full authorization flow with mock provider
- [ ] Integration: Event publishing via RabbitMQ
- [ ] Integration: Background job picks up AUTH_PENDING
- [ ] E2E: order.created → authorize → order.confirmed → capture → order listener updates

---

## Configuration to Provide

```yaml
payment:
  retry:
    max-attempts: 3
    initial-delay-ms: 1000
    multiplier: 2.0
    max-backoff-ms: 10000
  provider: mock  # or: stripe, adyen
  stripe:
    api-key: ${STRIPE_API_KEY}
  cleanup:
    enabled: true
    failed-retention-days: 365
    captured-retention-days: 2555  # 7 years
```

---

## Environment Variables

```bash
PAYMENT_PROVIDER=mock                          # mock | stripe | adyen
PAYMENT_RETRY_MAX_ATTEMPTS=3
PAYMENT_RETRY_INITIAL_DELAY_MS=1000
PAYMENT_RETRY_MULTIPLIER=2.0

# Database
POSTGRES_DB=paymentsdb
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# RabbitMQ (inherited from docker-compose)
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672

# Kafka for structured logging
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Tracing
OTEL_EXPORTER_OTLP_ENDPOINT=http://zipkin:9411
```

---

## Files You'll Create/Modify

```
services/payment-service/
├── src/main/java/nik/kalomiris/payment_service/
│   ├── PaymentServiceApplication.java
│   ├── domain/
│   │   ├── Payment.java (JPA entity)
│   │   └── PaymentStatus.java (enum)
│   ├── repository/
│   │   └── PaymentRepository.java
│   ├── service/
│   │   ├── PaymentService.java (business logic)
│   │   ├── PaymentRecoveryJob.java (background job)
│   │   └── PaymentRetryConfig.java (retry configuration)
│   ├── provider/
│   │   ├── PaymentProvider.java (interface)
│   │   ├── MockPaymentProvider.java
│   │   └── dto/ (ProviderAuthResult, etc.)
│   ├── controller/
│   │   └── PaymentController.java (REST endpoints)
│   ├── listeners/
│   │   └── PaymentOrderEventListener.java (RabbitMQ)
│   ├── config/
│   │   ├── RabbitMQConfig.java (queues, exchanges)
│   │   └── PaymentMetricsConfig.java (metrics)
│   └── dto/
│       ├── PaymentRequest.java
│       └── PaymentResponse.java
├── src/test/java/nik/kalomiris/payment_service/
│   ├── service/PaymentServiceTest.java
│   ├── listeners/PaymentOrderEventListenerTest.java
│   └── integration/PaymentIntegrationTest.java
├── pom.xml
├── Dockerfile
└── HELP.md

services/event-contracts/
└── src/main/java/nik/kalomiris/events/dtos/payment/
    ├── PaymentAuthorizedEvent.java
    ├── PaymentAuthorizationFailedEvent.java
    ├── PaymentCapturedEvent.java
    ├── PaymentCaptureFailedEvent.java
    ├── PaymentRefundedEvent.java
    ├── PaymentRefundFailedEvent.java
    └── PaymentVoidedEvent.java
```

---

## Quick Debugging Guide

| Problem | Check |
|---------|-------|
| Events not being processed | Queues created? `docker-compose ps` shows RabbitMQ running? |
| Idempotency not working | Check `findByOrderId` query, verify index exists |
| Retries not happening | Confirm `RetryTemplate` bean created, check exception type |
| Background job not running | Verify `@Scheduled` enabled, check logs for trigger |
| Payment stuck in AUTH_PENDING | Check background job, verify max_retries config, check provider logs |
| Events not published | Verify `TransactionSynchronization` called, check `@Transactional` |
| Optimistic lock failures | Expected for concurrent updates, use `RetryUtils.retryOnOptimisticLock()` |

