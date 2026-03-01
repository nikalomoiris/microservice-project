# Implementation Checklist — Payment Service

Use this checklist as you implement each phase. Check off tasks as you complete them.

---

## Pre-Implementation Setup

- [ ] Read DOCUMENT_INDEX.md to understand all resources
- [ ] Read THREE_CONCERNS_VISUAL_GUIDE.md (core concepts)
- [ ] Review PAYMENT_SERVICE_QUICK_REFERENCE.md (keep open!)
- [ ] Clone/understand order-service structure
- [ ] Understand existing RabbitMQ setup
- [ ] Verify docker-compose working
- [ ] Understand current CODEMAP.md structure

---

## Phase 1: Foundation (Project Setup)

### Module Creation
- [ ] Create `services/payment-service/` directory
- [ ] Create `services/payment-service/pom.xml` with dependencies:
  - [ ] Spring Boot 3.5.7
  - [ ] Spring Web
  - [ ] Spring Data JPA
  - [ ] Spring AMQP
  - [ ] Spring Retry
  - [ ] Postgres driver
  - [ ] logging-client
  - [ ] Micrometer (tracing, metrics)
- [ ] Add payment-service module to root `pom.xml`
- [ ] Create `PaymentServiceApplication.java` class
- [ ] Create `application.yml` configuration

### Docker & Compose
- [ ] Create `services/payment-service/Dockerfile`
- [ ] Add payment-service definition to `docker-compose.yml`:
  - [ ] Port 8084
  - [ ] PAYMENT_PROVIDER=mock
  - [ ] Retry configuration env vars
  - [ ] Postgres connection vars
- [ ] Ensure `services/create-databases.sh` creates `paymentsdb`
- [ ] Test: `docker-compose up` — payment-service should start

### Verification
- [ ] `mvn clean install` succeeds
- [ ] `docker-compose up` brings service online
- [ ] Service logs show successful startup
- [ ] Spring actuator endpoint responds: `curl http://localhost:8084/actuator`

---

## Phase 2: Domain Model & Database

### Entity Definition
- [ ] Create `domain/Payment.java` JPA entity with:
  - [ ] `@Id UUID id`
  - [ ] `@Column orderId UUID (indexed, unique)`
  - [ ] `@Enumerated status (CREATED, AUTH_PENDING, AUTHORIZED, CAPTURED, REFUNDED, VOIDED, FAILED)`
  - [ ] `amount NUMERIC(12,2)`
  - [ ] `currency CHAR(3)`
  - [ ] `providerIntentId VARCHAR(128) UNIQUE`
  - [ ] `retryCount INT DEFAULT 0`
  - [ ] `@Version version BIGINT (optimistic locking)`
  - [ ] `authorizedAt, capturedAt, refundedTotal, createdAt, updatedAt`

- [ ] Create `domain/PaymentStatus.java` enum:
  ```java
  public enum PaymentStatus {
      CREATED, AUTH_PENDING, AUTHORIZED, CAPTURED, REFUNDED, VOIDED, FAILED
  }
  ```

### Repository
- [ ] Create `repository/PaymentRepository.java` extending `JpaRepository<Payment, UUID>`
- [ ] Add custom queries:
  - [ ] `Optional<Payment> findByOrderId(String orderId)`
  - [ ] `List<Payment> findByStatusAndRetryCountLessThan(PaymentStatus, int)`
  - [ ] `List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus, LocalDateTime)`

### Database Schema
- [ ] Create migration file with:
  ```sql
  CREATE TABLE payments (
      id UUID PRIMARY KEY,
      order_id UUID NOT NULL UNIQUE,
      status VARCHAR(32) NOT NULL,
      amount NUMERIC(12,2) NOT NULL,
      currency CHAR(3) NOT NULL,
      provider_intent_id VARCHAR(128),
      authorized_at TIMESTAMP,
      captured_at TIMESTAMP,
      refunded_total NUMERIC(12,2) DEFAULT 0,
      retry_count INT DEFAULT 0,
      version BIGINT NOT NULL,
      created_at TIMESTAMP DEFAULT NOW(),
      updated_at TIMESTAMP DEFAULT NOW()
  );
  CREATE INDEX idx_payments_order_id ON payments(order_id);
  CREATE INDEX idx_payments_status ON payments(status);
  CREATE INDEX idx_payments_auth_pending ON payments(created_at) WHERE status='AUTH_PENDING';
  ```
- [ ] Run migration
- [ ] Verify schema in `paymentsdb`

### Testing
- [ ] Unit test: `PaymentEntityTest` verifies JPA mapping
- [ ] Integration test: Can save/retrieve payment from database

---

## Phase 3: Provider Abstraction

### Interface Definition
- [ ] Create `provider/PaymentProvider.java` interface:
  ```java
  public interface PaymentProvider {
      ProviderAuthResult authorize(Payment payment);
      ProviderCaptureResult capture(Payment payment);
      ProviderRefundResult refund(Payment payment, BigDecimal amount);
      ProviderVoidResult voidPayment(Payment payment);
  }
  ```

### Result DTOs
- [ ] Create `provider/dto/ProviderAuthResult.java`:
  - [ ] `success: boolean`
  - [ ] `intentId: String`
  - [ ] `errorCode: String`
  - [ ] `errorMessage: String`
- [ ] Create similar DTOs for capture, refund, void

### Mock Implementation
- [ ] Create `provider/MockPaymentProvider.java` implementing `PaymentProvider`
- [ ] Implement `@Bean` conditional on `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock")`
- [ ] Mock behavior:
  - [ ] Default success
  - [ ] Configurable failure rate
  - [ ] Deterministic based on payment ID (for testing)
  - [ ] Simulate timeout/network errors

### Testing
- [ ] Unit test: `MockPaymentProviderTest` verifies mock behavior
- [ ] Mock returns success by default
- [ ] Mock can simulate failures
- [ ] Mock is deterministic (same input = same output)

---

## Phase 4: Service Layer

### Retry Configuration
- [ ] Create `config/PaymentRetryConfig.java` with `@ConfigurationProperties(prefix = "payment.retry")`
- [ ] Properties:
  - [ ] `maxAttempts: 3`
  - [ ] `initialDelayMs: 1000`
  - [ ] `multiplier: 2.0`
  - [ ] `maxBackoffMs: 10000`
- [ ] Create `@Bean RetryTemplate` using properties

### Payment Service
- [ ] Create `service/PaymentService.java` with:
  - [ ] `createAndAuthorize(PaymentRequest): Payment`
    - [ ] Check if payment exists for orderId (idempotency)
    - [ ] Create payment in CREATED state
    - [ ] Try authorize with RetryTemplate
    - [ ] If success → AUTHORIZED
    - [ ] If all retries fail → AUTH_PENDING
    - [ ] Publish event after commit
  - [ ] `capturePayment(paymentId): Payment`
    - [ ] Find payment (must be AUTHORIZED)
    - [ ] Try capture with RetryTemplate
    - [ ] If success → CAPTURED
    - [ ] If retry fail → AUTH_PENDING
    - [ ] Publish event after commit
  - [ ] `refundPayment(paymentId, amount): Payment`
    - [ ] Similar pattern to capture
  - [ ] `voidPayment(paymentId): Payment`
    - [ ] Check if AUTHORIZED
    - [ ] Call provider.void()
    - [ ] Mark VOIDED
    - [ ] Publish event

### Recovery Job
- [ ] Create `service/PaymentRecoveryJob.java` with:
  - [ ] `@Scheduled(fixedDelay = 300000)` (5 minutes)
  - [ ] Query AUTH_PENDING with retry_count < 10
  - [ ] For each: retry authorize/capture
  - [ ] Update retry_count and status
  - [ ] Publish success or failure event
  - [ ] Max 10 total attempts → FAILED

### Structured Logging
- [ ] Create logs in PaymentService methods:
  - [ ] Authorization start/success/failure
  - [ ] Capture start/success/failure
  - [ ] Refund start/success/failure
  - [ ] Void start/success/failure
- [ ] Include: paymentId, orderId, status, retryCount, providerIntentId

### Testing
- [ ] Unit test: `PaymentServiceTest` with state transitions
- [ ] Test: CREATED → AUTH_PENDING → AUTHORIZED
- [ ] Test: Retry succeeds on 2nd attempt
- [ ] Test: Max retries → AUTH_PENDING marked
- [ ] Test: Idempotency (duplicate request skipped)
- [ ] Test: Events published after commit

---

## Phase 5: REST API

### DTOs
- [ ] Create `dto/PaymentRequest.java`:
  - [ ] `orderId: String @NotNull`
  - [ ] `amount: BigDecimal @Positive`
  - [ ] `currency: String @Pattern("[A-Z]{3}")`
  - [ ] `attemptAuthorize: boolean`
- [ ] Create `dto/PaymentResponse.java`:
  - [ ] All payment fields
  - [ ] `traceId` for debugging

### Controller
- [ ] Create `controller/PaymentController.java` with:
  - [ ] `POST /api/payments` → create & authorize
  - [ ] `GET /api/payments/{paymentId}` → get payment
  - [ ] `GET /api/payments?orderId=...&status=...` → list/filter
  - [ ] `POST /api/payments/{paymentId}/authorize` → retry authorize
  - [ ] `POST /api/payments/{paymentId}/capture` → capture
  - [ ] `POST /api/payments/{paymentId}/refund` → refund
  - [ ] All endpoints return PaymentResponse

### Error Handling
- [ ] Create `@RestControllerAdvice` with exception handlers:
  - [ ] `PaymentNotFoundException` → 404
  - [ ] `InvalidPaymentStatusException` → 400
  - [ ] `ValidationException` → 400
  - [ ] Generic `Exception` → 500 (with traceId)

### Validation
- [ ] Use `@Valid` and `@Validated`
- [ ] Amount > 0
- [ ] Currency is ISO 4217 (3 uppercase letters)
- [ ] OrderId is UUID format

### Testing
- [ ] Integration test: POST /api/payments → 201 Created
- [ ] Integration test: GET /api/payments/{id} → 200 OK
- [ ] Integration test: GET /api/payments?orderId=X → list returned
- [ ] Integration test: Validation errors return 400
- [ ] Integration test: Not found returns 404

---

## Phase 6: Event Integration

### Event DTOs
- [ ] In `services/event-contracts/src/main/java/nik/kalomiris/events/dtos/payment/`:
  - [ ] `PaymentAuthorizedEvent` with paymentId, orderId, status
  - [ ] `PaymentAuthorizationFailedEvent` with reason
  - [ ] `PaymentCapturedEvent`
  - [ ] `PaymentCaptureFailedEvent`
  - [ ] `PaymentRefundedEvent`
  - [ ] `PaymentRefundFailedEvent`
  - [ ] `PaymentVoidedEvent`

### RabbitMQ Configuration
- [ ] Create `config/RabbitMQConfig.java` with:
  - [ ] Exchange: `payment-exchange` (direct)
  - [ ] Queues:
    - [ ] `order.created.queue` (from order-exchange)
    - [ ] `order.confirmed.queue` (from order-exchange)
    - [ ] `order.cancelled.queue` (from order-exchange)
  - [ ] Bindings connecting to order-exchange
  - [ ] Constants for all routing keys and queue names

### Event Listeners
- [ ] Create `listeners/PaymentOrderEventListener.java` with:
  - [ ] `@RabbitListener` for order.created event
    - [ ] Idempotency check: `findByOrderId`
    - [ ] If exists: skip (log as duplicate)
    - [ ] If not: call `paymentService.createAndAuthorize`
  - [ ] `@RabbitListener` for order.confirmed event
    - [ ] Idempotency check: `findByOrderIdAndStatus(AUTHORIZED)`
    - [ ] If exists: skip (log as duplicate)
    - [ ] If not: call `paymentService.capturePayment`
  - [ ] `@RabbitListener` for order.cancelled event
    - [ ] Idempotency check: check status
    - [ ] If AUTHORIZED: call `paymentService.voidPayment`
    - [ ] If other states: mark VOIDED

### Event Publishing
- [ ] PaymentService publishes events post-commit using:
  ```java
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
          @Override
          public void afterCommit() {
              rabbitTemplate.convertAndSend(...);
          }
      }
  );
  ```

### Testing
- [ ] Integration test: Receive order.created → payment created
- [ ] Integration test: Duplicate order.created → idempotent
- [ ] Integration test: Receive order.confirmed → payment captured
- [ ] Integration test: Receive order.cancelled → payment voided
- [ ] Integration test: Events published to payment-exchange

---

## Phase 7: Observability

### Structured Logging
- [ ] Add LogPublisher bean injection to PaymentService
- [ ] Log all key operations with metadata:
  - [ ] Payment creation (orderId, amount, currency)
  - [ ] Authorization attempts (including retry count)
  - [ ] Authorization success/failure
  - [ ] Capture success/failure
  - [ ] Refund success/failure
  - [ ] Void success/failure
  - [ ] Idempotent duplicate detection

### Metrics
- [ ] Create micrometer metrics:
  - [ ] Counter: `payment.authorizations.total` with status tag
  - [ ] Counter: `payment.captures.total` with status tag
  - [ ] Counter: `payment.refunds.total` with status tag
  - [ ] Timer: `payment.provider.latency` with operation tag
  - [ ] Gauge: `payment.pending.authorizations` (count of AUTH_PENDING)

### Tracing
- [ ] Manual spans for provider calls:
  - [ ] Span name: `payment.provider.authorize`
  - [ ] Tags: provider, operation, success
  - [ ] Span links to incoming HTTP/RabbitMQ span

### Testing
- [ ] Verify logs appear in application output
- [ ] Verify metrics available at `/actuator/metrics`
- [ ] Verify traces appear in Zipkin UI

---

## Phase 8: Testing

### Unit Tests
- [ ] `PaymentServiceTest`:
  - [ ] State transitions (CREATED → AUTH_PENDING → AUTHORIZED)
  - [ ] Retry succeeds on 2nd attempt
  - [ ] All retries exhausted → AUTH_PENDING
  - [ ] Idempotency (duplicate request returns existing)
  - [ ] Capture only works on AUTHORIZED
  - [ ] Void only works on AUTHORIZED

- [ ] `PaymentControllerTest`:
  - [ ] POST /api/payments → 201 Created
  - [ ] GET /api/payments/{id} → 200 OK
  - [ ] Validation errors → 400 Bad Request
  - [ ] Not found → 404

### Integration Tests
- [ ] `PaymentServiceIntegrationTest`:
  - [ ] Full flow with Testcontainers (Postgres + RabbitMQ)
  - [ ] Create payment and authorize
  - [ ] Verify payment saved to DB
  - [ ] Verify event published
  - [ ] Capture previously authorized payment
  - [ ] Verify capture event published

### Idempotency Tests
- [ ] `PaymentIdempotencyTest`:
  - [ ] Send order.created twice → only 1 payment created
  - [ ] Send order.confirmed twice → only 1 capture executed
  - [ ] Send order.cancelled twice → only 1 void executed
  - [ ] Verify metrics don't double-count

### E2E Tests
- [ ] Extend existing e2e tests in `e2e-tests/`:
  - [ ] Create order → triggers payment authorization
  - [ ] Order confirmation → triggers payment capture
  - [ ] Verify payment captured event

### Coverage Target
- [ ] > 90% branch coverage on PaymentService
- [ ] All idempotency scenarios tested
- [ ] All state transitions tested
- [ ] All error cases tested

---

## Phase 9: Documentation

### Service Documentation
- [ ] Create `services/payment-service/HELP.md`:
  - [ ] Quick start guide
  - [ ] Configuration options
  - [ ] Known limitations
  - [ ] Future enhancements

### CODEMAP Update
- [ ] Add payment-service section:
  ```markdown
  ## payment-service (Port 8084)
  Manages payment authorization, capture, and refunds for orders.
  
  **Responsibilities:**
  - Authorize payments on order.created
  - Capture payments on order.confirmed
  - Refund payments on request
  - Void authorizations on order.cancelled
  
  **Key Components:**
  - PaymentService: State machine, retry logic
  - PaymentProvider: Abstraction for payment processors
  - PaymentRecoveryJob: Background job for stuck payments
  - PaymentOrderEventListener: RabbitMQ event handlers
  ```

### Service Topology Update
- [ ] Add payment-exchange to `docs/service-topology.md`
- [ ] Add message flows:
  - [ ] order.created → payment.authorized
  - [ ] order.confirmed → payment.captured
  - [ ] order.cancelled → payment.voided
- [ ] Add state machine diagram
- [ ] Document retry strategy

### PR Checklist
- [ ] Update `docs/PR_CHECKLIST.md` with payment-service deployment steps

### Testing Documentation
- [ ] Document how to run tests:
  - [ ] Unit tests: `mvn test`
  - [ ] Integration tests: `mvn integration-test`
  - [ ] E2E tests: `mvn -f e2e-tests/pom.xml test`

---

## Post-Implementation Verification

### Build & Startup
- [ ] `mvn clean install` succeeds
- [ ] `docker-compose up -d` brings all services online
- [ ] Payment service logs show successful startup
- [ ] Postgres `paymentsdb` is created and accessible

### Manual Testing
- [ ] Create payment: `curl -X POST http://localhost:8084/api/payments -H 'Content-Type: application/json' -d '{"orderId":"test-1","amount":100.00,"currency":"USD","attemptAuthorize":true}'`
- [ ] Get payment: `curl http://localhost:8084/api/payments/{paymentId}`
- [ ] Verify payment is in AUTHORIZED state
- [ ] Verify event published to RabbitMQ

### End-to-End Flow
- [ ] Create order (via order-service) → triggers payment authorization
- [ ] Check payment-service has payment in AUTHORIZED state
- [ ] Confirm order → triggers payment capture
- [ ] Check payment-service has payment in CAPTURED state
- [ ] Verify payment.captured event published

### Observability
- [ ] Check logs in OpenSearch (if configured)
- [ ] Check metrics in Prometheus
- [ ] Check traces in Zipkin

### Cleanup
- [ ] Fix any remaining warnings from build
- [ ] Ensure all tests pass
- [ ] Code compiles with no warnings
- [ ] All documentation updated

---

## Success Criteria

- [ ] All 9 phases complete
- [ ] >90% test coverage
- [ ] All idempotency tests pass
- [ ] Background recovery job verified
- [ ] CODEMAP.md and service-topology.md updated
- [ ] HELP.md created
- [ ] Manual end-to-end flow works
- [ ] Observability working (logs, metrics, traces)
- [ ] Code review checklist passed (PR_CHECKLIST.md)
- [ ] Ready for deployment

---

## Notes

- Use PAYMENT_SERVICE_QUICK_REFERENCE.md for code templates
- Reference order-service for similar patterns
- Test as you go (not after)
- Commit after each phase
- Update documentation incrementally

**You've got this! 🚀**

