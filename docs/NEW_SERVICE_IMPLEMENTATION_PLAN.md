# Payment Service — Implementation Plan

This plan details implementation of a new `payment-service` microservice, provider-agnostic and testable with a mock payment provider. Changes must remain backward-compatible with existing order workflow; event additions follow deployment checklist.

## 1. Overview
- Name: `payment-service`
- Purpose: Authorize, capture, refund payments for orders while abstracting external payment providers.
- Responsibilities:
  - Manage payment intents tied to `order-service` orders.
  - Authorize payments upon `order.created` or explicit client request.
  - Capture authorized payments after `order.confirmed`.
  - Publish payment lifecycle events consumed by `order-service` (and future reporting/notification services).
  - Provide refund capability post capture.
- Non-goals:
  - Storing full PCI data (only provider tokens / references).
  - Direct fraud detection logic.
  - Multi-currency FX conversion (assume amount + currency passed in).
- Tech: Spring Boot 3.5.7, Java 21, Maven module, Postgres (`paymentsdb`), RabbitMQ (domain + order exchange), Kafka (structured logs), Zipkin/OpenSearch tracing.

## 2. API Design (REST)
- Base path: `/api/payments`
- Endpoints:
  - `POST /api/payments` — Create payment intent & optionally authorize (fields: `orderId`, `amount`, `currency`, `attemptAuthorize=true|false`).
  - `GET /api/payments/{paymentId}` — Retrieve payment aggregate.
  - `GET /api/payments?orderId=...&status=...` — List/filter payments.
  - `POST /api/payments/{paymentId}/authorize` — Explicit authorization attempt (idempotent).
  - `POST /api/payments/{paymentId}/capture` — Capture authorized payment (idempotent).
  - `POST /api/payments/{paymentId}/refund` — Refund captured payment (partial: `amount` optional <= captured amount).
  - (Future) `GET /api/payments/{paymentId}/events` — Audit trail.
- DTOs (internal): request/response under service module. Cross-service events only in `event-contracts`.
- Error model: JSON with `code`, `message`, `traceId`.
- Validation: Bean Validation (`@NotNull`, `@Positive`, currency ISO 4217 regex `[A-Z]{3}`).
- Idempotency header (optional future): `Idempotency-Key` for client retries (store key hash to prevent duplicate side effects).

## 3. Messaging Contracts (RabbitMQ)
- Exchange: `payment-exchange` (new). Do NOT rename after creation without migration plan.
- Consumed events (from `order-exchange`):
  - `order.created` → create payment and authorize (idempotent: check if payment exists for orderId)
  - `order.confirmed` → capture authorized payment (idempotent: check if already captured)
  - `order.cancelled` → void authorization (idempotent: check if already voided)
- Published events (to `payment-exchange`):
  - `payment.authorized` (published after successful authorization)
  - `payment.authorization_failed` (published after max retries exhausted)
  - `payment.captured` (published after successful capture)
  - `payment.capture_failed` (published after max retries exhausted)
  - `payment.refunded` (published after successful refund)
  - `payment.refund_failed` (published after max retries exhausted)
  - `payment.voided` (published after successful authorization void)
- DTOs in `services/event-contracts/src/main/java/nik/kalomiris/events/dtos/payment/`:
  - `PaymentAuthorizedEvent`, `PaymentAuthorizationFailedEvent`
  - `PaymentCapturedEvent`, `PaymentCaptureFailedEvent`
  - `PaymentRefundedEvent`, `PaymentRefundFailedEvent`
  - `PaymentVoidedEvent`
- Publishing pattern (transaction post-commit):
  ```java
  @Transactional
  public void authorizePayment(Payment payment) {
      // ... authorization logic ...
      payment.setStatus(PaymentStatus.AUTHORIZED);
      paymentRepository.save(payment);
      
      // Publish event AFTER commit (prevents message loss)
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                  rabbitTemplate.convertAndSend(
                      PAYMENT_EXCHANGE, 
                      "payment.authorized",
                      new PaymentAuthorizedEvent(payment)
                  );
              }
          }
      );
  }
  ```
- All listeners are idempotent: check if work already done before applying state transition

## 4. Data Model & Storage
- Database: Postgres `paymentsdb`.
- Tables:
  - `payments`:
    - `id` UUID PK
    - `order_id` UUID (indexed, unique within retention window)
    - `status` VARCHAR(32) ENUM: CREATED, AUTH_PENDING, AUTHORIZED, CAPTURED, REFUNDED, VOIDED, FAILED
    - `amount` NUMERIC(12,2)
    - `currency` CHAR(3)
    - `provider_intent_id` VARCHAR(128) UNIQUE (external provider reference)
    - `authorized_at` TIMESTAMP NULL
    - `captured_at` TIMESTAMP NULL
    - `refunded_total` NUMERIC(12,2) DEFAULT 0
    - `retry_count` INT DEFAULT 0 (tracks authorization/capture/refund retry attempts)
    - `version` BIGINT (optimistic locking for concurrent operations)
    - `created_at` TIMESTAMP DEFAULT now()
    - `updated_at` TIMESTAMP DEFAULT now()
  - `payment_events` (audit, append-only): `id` BIGSERIAL PK, `payment_id` UUID, `type` VARCHAR, `payload` JSONB, `created_at` TIMESTAMP.
- Indexes: `idx_payments_order_id`, `idx_payments_status`, partial index on `status IN ('AUTH_PENDING', 'CREATED')` for background job queries.
- Transactions: All payment state transitions enclosed in single transaction; publish events after commit via `TransactionSynchronization`.
- Provisioning: add creation of `paymentsdb` to `services/create-databases.sh`.

### Data Retention Policy
- `CAPTURED`, `REFUNDED`, `VOIDED`: 7 years (compliance, chargebacks, tax audits, legal disputes)
- `FAILED`: 1 year (fraud analysis, failure patterns)
- `AUTH_PENDING`: investigate if > 7 days old; indicates stuck authorization
- `payment_events`: 7 years (audit trail for compliance; archive with matching payment records)

### Idempotency Strategy
- **Event deduplication:** Check if payment exists for `orderId` before creating/processing
- **Query:** `SELECT * FROM payments WHERE order_id = ?` (fast, indexed)
- **Pattern:** If exists, skip event (idempotent). If not, create payment and proceed.
- **Works because:** Once a payment is created for an order, duplicate RabbitMQ redeliveries find it and skip.
- **Separate from retention:** Archived payments (year+ old) can still prevent redelivery processing.

## 5. Provider Abstraction
- Interface: `PaymentProvider` with methods `authorize(PaymentContext)`, `capture(PaymentContext)`, `refund(PaymentContext, amount)` returning typed result objects (`ProviderAuthResult`, etc.).
- Implementations:
  - `MockPaymentProvider` (returns deterministic success/failure based on configured ratios or orderId hash; used in tests and dev).
  - Future real provider adapter(s) (Stripe, Adyen) placed under `provider` package.
- Selection: Spring profile or env var `PAYMENT_PROVIDER` (default `mock`).
- Testability: Integration tests use Testcontainers for Postgres + real RabbitMQ; provider is mock with deterministic behavior.

## 6. Observability
- Structured logs: Use `LogMessage.Builder()` including keys: `paymentId`, `orderId`, `status`, `providerIntentId`.
- Key log events: creation, authorization attempt (with outcome), capture attempt (with outcome), refund attempt, idempotent duplicate detected.
- Tracing: automatic HTTP + RabbitMQ instrumentation; wrap provider calls in a custom span `payment.provider.call` with tags `provider`, `operation`, `success`.
- Metrics (Micrometer):
  - Counter: `payment.authorizations.total{status=success|failed}`
  - Counter: `payment.captures.total{status=success|failed}`
  - Counter: `payment.refunds.total{status=success|failed}`
  - Timer: `payment.provider.latency{operation=authorize|capture|refund}`
  - Gauge: `payment.pending.authorizations` (count of AUTH_PENDING).

## 7. Failure Modes & Resilience

### Three Separate Concerns (Must be handled independently)

#### 1. Idempotency (Duplicate Event Handling)
- **Check:** Does payment already exist for this `orderId`?
- **Pattern:** If yes, return early (skip processing). If no, create and proceed.
- **Why:** RabbitMQ redeliveries will find existing payment and skip.
- **Scope:** Applies to all event listeners (`order.created`, `order.confirmed`, `order.cancelled`)

#### 2. Immediate Retry Logic (Transient Failures)
- **Tool:** Spring RetryTemplate with exponential backoff
- **Config:** `PAYMENT_RETRY_MAX_ATTEMPTS=3`, `PAYMENT_RETRY_INITIAL_DELAY_MS=1000`, `PAYMENT_RETRY_MULTIPLIER=2.0`
- **Applies to:** `authorize()`, `capture()`, `refund()` operations
- **Retry on:** Network timeouts, provider 5xx errors, connection failures
- **Skip retry:** Card declined, invalid request, bad data (non-transient)
- **After retries exhausted:** Mark payment `AUTH_PENDING` with `retry_count`, don't fail immediately

#### 3. Long-Term Recovery (Stuck Payments)
- **Mechanism:** Scheduled background job (every 5 minutes)
- **Query:** `SELECT * FROM payments WHERE status = 'AUTH_PENDING' AND retry_count < 10 AND created_at > NOW() - INTERVAL '7 days'`
- **Action:** Attempt authorization/capture again; increment `retry_count`
- **Terminal state:** After 10 total attempts across listener + background job, mark `FAILED`
- **Events:** Publish `payment.authorization_failed` or `payment.capture_failed` when max retries exhausted

### Event Handling: order.cancelled
- **When received:** `order.cancelled` event for a payment
- **Check payment status:**
  - If `AUTHORIZED`: Call `paymentProvider.void()`, set status to `VOIDED`, publish `payment.voided`
  - If `CREATED` or `AUTH_PENDING`: Just mark as `VOIDED` (no provider call)
  - If `CAPTURED` or beyond: Refund logic (future phase)
- **Benefit:** Customer funds released immediately, clean state

### Optimistic Locking
- **Field:** `version` BIGINT with `@Version` annotation
- **Use case:** Concurrent capture attempts (e.g., duplicate `order.confirmed` events arriving simultaneously)
- **Behavior:** First capture succeeds, second gets `OptimisticLockingFailureException` → idempotent retry helper
- **Pattern:** Wrap in `RetryUtils.retryOnOptimisticLock()` (similar to order-service)

## 8. Security & Config
- Secrets: provider API keys via env vars `PAYMENT_PROVIDER_KEY` (unused for mock).
- Sensitive data: store only last4 or masked tokens if needed later (out of scope now).
- Validation: amounts > 0, currency uppercase 3 letters; refund amount <= captured amount - refunded_total.
- RBAC: defer; existing stack has no auth layer.

## 9. Implementation Steps

### Phase 1: Foundation (Domain, Config, Provider)
1. Create module `services/payment-service/` with Spring Boot app class `PaymentServiceApplication`
2. Add `<module>payment-service</module>` to root `pom.xml`
3. Add POM dependencies:
   - Spring Web, Spring Data JPA, Postgres driver, Spring AMQP
   - Spring Retry (for RetryTemplate)
   - logging-client (for structured logging)
   - micrometer tracing, prometheus metrics
4. Create Dockerfile (consistent with other services, port 8084)
5. Add service to `docker-compose.yml` with:
   - `PAYMENT_PROVIDER=mock` (default)
   - `PAYMENT_RETRY_MAX_ATTEMPTS=3`, `PAYMENT_RETRY_INITIAL_DELAY_MS=1000`, `PAYMENT_RETRY_MULTIPLIER=2.0`
   - Postgres connection to `paymentsdb`
   - Ensure `services/create-databases.sh` creates `paymentsdb`

### Phase 2: Domain Model & Database
6. Implement domain model:
   - Entity: `Payment` (id, orderId, status, amount, currency, providerIntentId, retryCount, version, timestamps)
   - Enum: `PaymentStatus` (CREATED, AUTH_PENDING, AUTHORIZED, CAPTURED, REFUNDED, VOIDED, FAILED)
   - Repository: `PaymentRepository` with queries:
     - `findByOrderId(String orderId)`
     - `findByStatusAndRetryCountLessThan(PaymentStatus, int)` (for background job)
     - `findByStatusAndCreatedAtBefore(PaymentStatus, LocalDateTime)` (for cleanup)
7. Create migration/schema file with tables and indexes as defined in Section 4

### Phase 3: Provider Abstraction
8. Implement `PaymentProvider` interface:
   ```java
   public interface PaymentProvider {
       ProviderAuthResult authorize(Payment payment);
       ProviderCaptureResult capture(Payment payment);
       ProviderRefundResult refund(Payment payment, BigDecimal amount);
       ProviderVoidResult void(Payment payment);
   }
   ```
9. Implement `MockPaymentProvider`:
   - Deterministic results based on orderId hash or configurable ratios
   - Simulates success, declined cards, timeouts
   - Bean conditional on `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock")`
10. Create result DTOs: `ProviderAuthResult`, `ProviderCaptureResult`, `ProviderRefundResult`, `ProviderVoidResult`

### Phase 4: Service Layer
11. Implement `PaymentService` with state machine transitions
12. Implement `PaymentRetryConfig` bean with RetryTemplate
13. Implement background recovery job `PaymentRecoveryJob`

### Phase 5: REST API
14. Implement `PaymentController` with endpoints from Section 2
15. Add validation and error handling

### Phase 6: Event Integration
16. Create event DTOs in `services/event-contracts/.../payment/` package
17. Implement RabbitMQ listeners in `PaymentOrderEventListener`
18. Add RabbitMQ configuration

### Phase 7: Observability
19. Add structured logging via `LogPublisher`
20. Add metrics (Micrometer)
21. Add tracing for provider calls

### Phase 8: Testing
22. Write unit tests for state transitions and retry behavior
23. Write integration tests with Testcontainers
24. Write idempotency tests for duplicate events
25. Write E2E tests extending existing test suite

### Phase 9: Documentation
26. Create `payment-service/HELP.md`
27. Update root `CODEMAP.md`
28. Update `docs/service-topology.md` with payment flows
29. Update `docs/PR_CHECKLIST.md` with deployment steps

## 10. Testing Strategy
- Unit: JUnit + Mockito (PaymentService state transitions, provider adapter outcomes).
- Repository tests: verify optimistic locking behavior for concurrent capture attempts.
- Messaging idempotency: send same `order.confirmed` twice; assert single capture.
- Integration: `@SpringBootTest` with Testcontainers Postgres; mock provider bean; verify events published.
- E2E: Extend existing e2e tests to create order → authorize → confirm → capture; assert payment captured event consumed by order-service.

## 11. Rollout Plan
- Phase 1: Deploy payment-service (listeners for `order.created` / `order.confirmed` registered but producers unaffected).
- Phase 2: Enable order-service logic to react to payment events (update order status flows) — consumers deployed first.
- Backward compatibility: order-service should continue without payment events until Phase 2 toggle.
- Smoke steps:
  ```bash
  # Create payment intent (auto auth)
  curl -s -X POST http://localhost:8084/api/payments -H 'Content-Type: application/json' \
      -d '{"orderId":"<uuid>","amount":129.99,"currency":"USD","attemptAuthorize":true}' | jq .

  # Capture (simulate order confirmed)
  curl -s -X POST http://localhost:8084/api/payments/<paymentId>/capture | jq .
  ```
- Monitoring: Zipkin trace links; OpenSearch logs filtered by `service: "payment-service"`.
- Rollback: If provider instability, disable listeners via feature flag `PAYMENT_ENABLED=false` and revert order-service to direct confirmation path.

## 12. Risks & Mitigations
- Provider unavailability → Retry & fallback to delayed authorization.
- Race between order.confirmed and authorization completion → optimistic lock & queue capture after authorization event.
- Duplicate capture attempts → version field prevents double capture; duplicates produce idempotent log.
- Event ordering issues → rely on eventual consistency; store pending capture flag if capture arrives before authorization completion.

## 13. Acceptance Criteria
- All defined REST endpoints implemented with validation and error model.
- Payment lifecycle events published & consumed; idempotent under redelivery.
- Observability: metrics visible; logs correlate by `traceId`.
- Tests: >90% branch coverage on PaymentService; idempotency tests pass.
- Docs (`CODEMAP.md`, `service-topology.md`) updated with payment flows.
- No hardcoded provider logic; mock provider selectable via env.

## 14. Resolved Questions (Design Phase)

✅ **Automatic authorization trigger:** Always on `order.created` (no configuration needed for MVP)

✅ **Partial captures:** Not needed for MVP (always full capture after authorization)

✅ **Refund constraints:** No time-based limit (can refund anytime after capture) — can be added in future

✅ **Payment retry strategy:** RetryTemplate with 3 attempts, 1s→2s→4s backoff; then AUTH_PENDING for background job

✅ **Void authorization:** Yes, when order.cancelled arrives (releases held funds immediately)

✅ **Data retention:** CAPTURED/REFUNDED/VOIDED 7 years, FAILED 1 year, events 7 years

✅ **Idempotency pattern:** Check if payment exists for orderId; skip if exists

## 15. Future Enhancements (Out of Scope for MVP)
- Idempotency keys for POST endpoints (client-side retry safety)
- Partial captures per line item
- Refund time windows (e.g., no refunds after 90 days)
- Chargeback handling
- Real payment provider implementations (Stripe, Adyen)
- Fraud detection/scoring
- Multi-currency FX conversion
- Payment splitting across multiple providers
- Webhook handling for async provider updates

