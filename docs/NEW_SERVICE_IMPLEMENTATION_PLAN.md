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
  - `order.created` → start/authorize payment if auto mode enabled.
  - `order.confirmed` → capture payment.
  - `order.cancelled` → void/ignore depending on status (only if not captured).
- Published events (to `payment-exchange`):
  - `payment.requested`
  - `payment.authorized`
  - `payment.authorization_failed`
  - `payment.captured`
  - `payment.capture_failed`
  - `payment.refunded`
  - `payment.refund_failed`
- DTOs added in `services/event-contracts/.../payment` package (new subpackage):
  - `PaymentRequestedEvent`, `PaymentAuthorizedEvent`, `PaymentFailedEvent` (shared for authorization/capture with type), `PaymentCapturedEvent`, `PaymentRefundedEvent`.
- Publishing pattern (transaction post-commit):
  ```java
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
          rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, routingKey, eventDto);
      }
  });
  ```
- Idempotent consumers: check current payment status before applying transition; ignore duplicates.

## 4. Data Model & Storage
- Database: Postgres `paymentsdb`.
- Tables:
  - `payments`:
    - `id` UUID PK
    - `order_id` UUID (indexed)
    - `status` VARCHAR(32) (ENUM in code: CREATED, AUTH_PENDING, AUTHORIZED, CAPTURED, REFUNDED, FAILED)
    - `amount` NUMERIC(12,2)
    - `currency` CHAR(3)
    - `provider_intent_id` VARCHAR(128) UNIQUE (external provider reference)
    - `authorized_at` TIMESTAMP NULL
    - `captured_at` TIMESTAMP NULL
    - `refunded_total` NUMERIC(12,2) DEFAULT 0
    - `version` INT (optimistic locking)
    - `created_at` TIMESTAMP DEFAULT now()
    - `updated_at` TIMESTAMP DEFAULT now()
  - `payment_events` (audit, append-only): `id` BIGSERIAL PK, `payment_id` UUID, `type` VARCHAR, `payload` JSONB, `created_at` TIMESTAMP.
- Indexes: `idx_payments_order_id`, `idx_payments_status`, partial index on `status='AUTH_PENDING'` for capture workflow speed.
- Transactions: Payment state transitions enclosed in single transaction; publish events after commit.
- Provisioning: add creation of `paymentsdb` to `services/create-databases.sh`.

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

## 7. Failure Modes & Idempotency
- Duplicate events (redelivery): ignore if payment already at target status.
- Provider transient failure: retry with exponential backoff (config: `PAYMENT_RETRY_MAX_ATTEMPTS`, `PAYMENT_RETRY_INITIAL_DELAY_MS`). Store attempt count in `payment_events`.
- Network partitions: mark status AUTH_PENDING until successful authorization/capture or max retries → FAILED.
- Idempotency Keys: For client POST create intents; if same key reused, return existing intent.
- Compensations:
  - Failed capture after authorization: leave AUTHORIZED and schedule retry; eventual cancellation path if order times out.
  - Partial refund failure: produce `payment.refund_failed` event and allow manual re-attempt.

## 8. Security & Config
- Secrets: provider API keys via env vars `PAYMENT_PROVIDER_KEY` (unused for mock).
- Sensitive data: store only last4 or masked tokens if needed later (out of scope now).
- Validation: amounts > 0, currency uppercase 3 letters; refund amount <= captured amount - refunded_total.
- RBAC: defer; existing stack has no auth layer.

## 9. Implementation Steps
1. Create module `services/payment-service/` (Spring Boot app class `PaymentServiceApplication`).
2. Add `<module>payment-service</module>` to root `pom.xml`.
3. POM dependencies: Spring Web, Spring Data JPA, Postgres driver, Spring AMQP, logging-client, micrometer tracing.
4. Add Dockerfile (pattern consistent with other services).
5. Add service definition in `docker-compose.yml` with `PAYMENT_PROVIDER=mock` and DB connection envs; ensure `paymentsdb` creation.
6. Implement domain model `Payment` + repository, service layer `PaymentService` with state machine transitions.
7. Implement `PaymentProvider` interface + `MockPaymentProvider` bean conditional on profile.
8. Implement REST controller `PaymentController`.
9. Add event DTOs to `event-contracts` and publish events post-commit.
10. Add Rabbit listeners for `order.created`, `order.confirmed`, `order.cancelled`.
11. Add metrics + structured logging instrumentation.
12. Write unit tests (service, provider adapter, controller), idempotency tests (duplicate events), integration tests (provider mock + DB).
13. Update `CODEMAP.md` and `docs/service-topology.md` with payment flows.

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

## 14. Open Questions
- Automatic authorization trigger: always on `order.created` or configurable per order?
- Partial captures needed? (currently single full capture assumption.)
- Refund window & constraints (time-based limits)?
- SLA for payment latency and retry schedule? 

