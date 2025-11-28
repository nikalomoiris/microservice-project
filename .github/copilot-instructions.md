<!-- .github/copilot-instructions.md - guidance for AI coding agents -->
# Microservices Project — Copilot Instructions

Purpose: Equip AI coding agents with actionable knowledge to be immediately productive in this Spring Boot microservices monorepo.

## 1. Architecture Overview

**Tech Stack**: Spring Boot 3.5.7, Java 21, Maven multi-module, Docker Compose  
**Services** (`services/`): `product-service`, `inventory-service`, `order-service`, `review-service`, `logging-service`, `logging-client` (library), `event-contracts` (shared DTOs)  
**Infrastructure** (`docker-compose.yml`): Postgres (multi-DB: `productsdb`, `ordersdb`, `inventorydb`, `reviewsdb`), RabbitMQ, Kafka/Zookeeper, OpenSearch + Dashboards, Zipkin, Kafka Connect

**Communication Patterns**:
- REST APIs for synchronous calls (controllers at `/api/{resource}`)
- RabbitMQ topic exchanges for async events (product creation, order lifecycle)
- Kafka for structured logging pipeline (`logging-client` → Kafka → OpenSearch)
- Zipkin + OpenSearch for distributed tracing with trace-log correlation

**Key Design Principles**:
- Event-driven order workflow: `order.created` → inventory reservation → outcome events
- Idempotent message listeners (RabbitMQ redelivery tolerance)
- Structured logging with trace context (`traceId`, `spanId`) via `LogMessage.Builder()`
- Transaction-synchronized event publishing (post-commit hooks)

## 2. Critical File Locations

**Build & Config**:
- Root POM: `pom.xml` (defines modules, Spring Boot 3.5.7 parent)
- Service POMs: `services/*/pom.xml` (dependencies, packaging)
- Docker Compose: `docker-compose.yml` (infra + service ports)
- DB initialization: `services/create-databases.sh` (mounted to Postgres entrypoint)

**Domain & APIs**:
- Controllers: `services/*/src/main/java/**/controller/*Controller.java` or `services/*/src/main/java/**/*Controller.java`
- Services: `services/*/src/main/java/**/service/*Service.java`
- Entities: `services/*/src/main/java/**/domain/*.java`
- DTOs/Events: `services/event-contracts/src/main/java/**/*.java` (shared across services)

**Messaging**:
- RabbitMQ Config: `services/inventory-service/src/main/java/.../config/RabbitMQConfig.java` (canonical constants)
- Listeners: `services/*/src/main/java/**/listeners/*Listener.java` (annotated with `@RabbitListener`)
- Exchange constants: `PRODUCT_EXCHANGE_NAME = "product-exchange"`, `ORDER_EXCHANGE_NAME = "order-exchange"`

**Observability**:
- Structured logging: `services/logging-client/STRUCTURED_LOGGING.md`, `services/logging-client/src/main/java/*/LogMessage.java`
- Tracing setup: `docs/DISTRIBUTED_TRACING_IMPLEMENTATION_PLAN.md`, `docs/TRACING_AND_OBSERVABILITY.md`
- Dashboards: `ops/opensearch/saved_objects/*.ndjson`

**Documentation**:
- Service responsibilities: `CODEMAP.md`
- Message flows: `docs/service-topology.md`, `docs/message-flow.md`
- Implementation plans: `docs/*_IMPLEMENTATION_PLAN.md` (comprehensive templates for features)

## 3. Build, Run & Test Commands

**Build all modules**:
```bash
mvn clean install
```

**Start full stack** (infra + all services):
```bash
docker-compose up -d
```

**Run single service locally**:
```bash
# Option A: from service directory
cd services/product-service && ./mvnw spring-boot:run

# Option B: from repo root
mvn -pl services/product-service -am spring-boot:run
```

**Run unit tests** (CI runs per-module):
```bash
# All modules
mvn -B -T1C clean test

# Single module
mvn -pl services/order-service -am test
```

**Run E2E tests** (Testcontainers-based):
```bash
mvn -f e2e-tests/pom.xml test
```

**Provision observability stack**:
```bash
./ops/scripts/provision-logs-pipeline.sh   # Kafka → OpenSearch logs
./ops/scripts/provision-tracing.sh          # Zipkin → OpenSearch traces
./scripts/test-distributed-tracing.sh       # Generate sample traces
```

## 4. Messaging Contracts (RabbitMQ)

**Source of truth**: `services/inventory-service/src/main/java/.../config/RabbitMQConfig.java`

**Product Events**:
- Exchange: `product-exchange`
- Routing key: `product.created`
- Consumer: `inventory-service` → `ProductEventListener`

**Order Events**:
- Exchange: `order-exchange`
- Routing keys:
  - `order.created` → inventory reservation attempt
  - `order.confirmed` → inventory commit
  - `order.inventory.reserved` → order status update (success)
  - `order.inventory.reservation_failed` → order status update (failure)
  - `order.inventory.committed` → final inventory commit
- Consumers: `inventory-service` → `OrderEventListener`, `order-service` → `InventoryEventListener`

**Critical Rule**: Never change routing keys or exchange names without coordinated deployment (see `docs/PR_CHECKLIST.md`).

## 5. Project-Specific Conventions

**Package Naming**: Uses underscores (e.g., `nik.kalomiris.product_service`, `nik.kalomiris.order_service`). Follow existing patterns—do NOT rename packages globally.

**Structured Logging** (`logging-client`):
```java
// Use LogMessage.Builder() instead of console prints
LogMessage log = new LogMessage.Builder()
    .message("Order created")
    .level("INFO")
    .service("order-service")
    .metadata(Map.of("orderNumber", orderNumber, "totalAmount", amount))
    .build();
logPublisher.publish(log);
```
- Trace context (`traceId`, `spanId`) auto-injected from Spring Micrometer Tracing
- See `services/logging-client/STRUCTURED_LOGGING.md`

**Event Publishing**:
- Use transaction synchronization to publish events AFTER DB commit:
  ```java
  TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
          @Override
          public void afterCommit() {
              rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
          }
      }
  );
  ```
- Example: `services/order-service/src/main/java/.../service/OrderService.java`

**Idempotency**:
- All `@RabbitListener` methods must handle redelivery (duplicate messages)
- Check entity state before applying transitions (e.g., `order.getStatus()` before updating)
- Add tests for duplicate event handling

**Tracing**:
- RabbitMQ auto-instrumented (set `rabbitTemplate.setObservationEnabled(true)`)
- HTTP auto-instrumented by Spring Boot Actuator + Micrometer
- Kafka manual: `logging-client` extracts trace context from `Tracer` bean

## 6. Common Development Tasks

**Add new REST endpoint**:
1. Update controller: `services/{service}/src/main/java/**/controller/*Controller.java`
2. Add service method: `services/{service}/src/main/java/**/service/*Service.java`
3. Add DTOs if needed: `services/{service}/src/main/java/**/dto/` or `services/event-contracts`
4. Add unit tests: `services/{service}/src/test/java/**/*Test.java`

**Add new event type**:
1. Create DTO in `services/event-contracts/src/main/java/nik/kalomiris/events/dtos/`
2. Update producer to publish event (add routing key constant if new)
3. Update consumer listener to handle event
4. Update `docs/service-topology.md` with event flow diagram
5. Follow deployment checklist in `docs/PR_CHECKLIST.md` (consumers first!)

**Modify RabbitMQ topology**:
1. Update constants in `services/inventory-service/src/main/java/.../config/RabbitMQConfig.java`
2. Update all services that reference the changed exchange/routing key
3. Update `docs/service-topology.md`
4. Coordinate deployment: deploy consumers before producers (or add backward compatibility)

**Add new service**:
1. Create module under `services/{new-service}/`
2. Add `<module>` entry in root `pom.xml`
3. Add Dockerfile in service root
4. Add service to `docker-compose.yml` (ports, env vars, dependencies)
5. Add database creation to `services/create-databases.sh` if needed
6. Update `CODEMAP.md` and `docs/service-topology.md`

**Add new service**:
1. Create module under `services/{new-service}/`
2. Add `<module>` entry in root `pom.xml`
3. Add Dockerfile in service root
4. Add service to `docker-compose.yml` (ports, env vars, dependencies)
5. Add database creation to `services/create-databases.sh` if needed
6. Update `CODEMAP.md` and `docs/service-topology.md`

## 7. Testing Patterns

**Unit Tests** (per service):
- Location: `services/*/src/test/java/**/*Test.java`
- Use JUnit 5 (`@Test`), Mockito for mocks
- Example: `services/order-service/src/test/java/.../service/OrderServiceConfirmOrderTest.java`

**Integration Tests**:
- Use `@SpringBootTest` for full Spring context
- Example: `services/order-service/src/test/java/.../integration/InventoryReservationIntegrationTest.java`

**E2E Tests** (Testcontainers):
- Location: `e2e-tests/src/test/java/**/*Test.java`
- Starts full `docker-compose.yml` stack
- Tests cross-service flows (product → inventory → order)
- Run: `mvn -f e2e-tests/pom.xml test`

**Idempotency Tests**:
- Essential for all `@RabbitListener` methods
- Test: send same event twice, verify state consistency
- Example pattern in `services/order-service/src/test/java/.../listeners/InventoryEventListenerTest.java`

## 8. Observability & Debugging

**Structured Logging**:
- Query logs in OpenSearch: http://localhost:5601 → Index Pattern: `service-logs-*`
- Filter by service: `service: "order-service"`
- Correlate by trace: `traceId: "<trace-id-from-zipkin>"`

**Distributed Tracing**:
- Zipkin UI: http://localhost:9411
- OpenSearch traces: Index Pattern: `zipkin-span-*`
- Pre-built dashboards: `ops/opensearch/saved_objects/tracing-dashboard.ndjson`
- Trace-log correlation: logs and spans share `traceId`

**OpenSearch Queries** (see `ops/opensearch/TRACING_QUERIES.md`):
```json
// Find spans for a specific trace
GET zipkin-span-*/_search
{
  "query": { "term": { "traceId": "abc123" } }
}

// Find logs with errors for a trace
GET service-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "term": { "traceId": "abc123" } },
        { "term": { "level": "ERROR" } }
      ]
    }
  }
}
```

**Service Ports** (from `docker-compose.yml`):
- product-service: 8080
- order-service: 8081
- review-service: 8082
- inventory-service: 8083
- logging-service: 8090
- RabbitMQ Management: 15672 (guest/guest)
- Zipkin: 9411
- OpenSearch Dashboards: 5601

## 9. CI/CD Workflows

**GitHub Actions** (`.github/workflows/`):
- `ci.yml`: Unit tests per module (matrix strategy)
- `e2e.yml`: End-to-end tests (Testcontainers)
- `codeql.yml`: Security scanning

**CI Build Command**:
```bash
mvn -B -T1C clean install  # parallel build
mvn -f e2e-tests/pom.xml test
```

**Per-module Testing**:
```bash
# CI runs tests per module to parallelize
mvn -pl services/order-service -am clean test
```

## 10. What to Avoid / Preserve

**Breaking Changes**:
- ❌ Never change RabbitMQ exchange/routing key names without rollout plan
- ❌ Never rename packages globally (keep underscore convention)
- ❌ Never modify event DTO shapes without backward compatibility or coordinated deployment
- ❌ Never hardcode infrastructure hostnames (use `docker-compose.yml` names)

**Deployment Order for Event Changes** (see `docs/PR_CHECKLIST.md`):
1. Deploy consumers first (can handle new event shape)
2. Deploy producers (start sending new shape)
3. Monitor logs/metrics for failures

**Backward Compatibility**:
- Add new fields to events as optional
- Support both old and new shapes in consumers during migration
- Use feature flags for gradual rollout if needed

## 11. PR & Planning Guidelines

**PR Structure** (small, self-contained):
- Short summary with concrete files changed
- Manual smoke test steps (curl commands)
- For event changes: include sample JSON and deployment order
- Reference `docs/PR_CHECKLIST.md` for event-driven changes

**Implementation Plans** (for features/epics):
- Create comprehensive plan in `docs/` using template pattern
- See examples: `docs/REVIEW_EVALUATION_IMPLEMENTATION_PLAN.md`, `docs/DISTRIBUTED_TRACING_IMPLEMENTATION_PLAN.md`
- Include: architecture diagrams, data models, API design, testing strategy, deployment plan, risk analysis
- Break into phases with acceptance criteria and effort estimates
- Link to GitHub issues for tracking

**Documentation Updates** (when applicable):
- `CODEMAP.md`: Add/update service responsibilities
- `docs/service-topology.md`: Update message flows and diagrams
- `docs/PR_CHECKLIST.md`: Update rollout checklists if process changes
- Service-level `HELP.md`: Add service-specific dev notes

## 12. Quick Reference Files

| File | Purpose |
|------|---------|
| `CODEMAP.md` | Service responsibilities and entry points |
| `README.md` | High-level overview, badges, quick start |
| `docker-compose.yml` | Infrastructure and service definitions |
| `docs/service-topology.md` | Message flows, exchanges, routing keys |
| `docs/PR_CHECKLIST.md` | Event-driven change deployment checklist |
| `docs/TRACING_AND_OBSERVABILITY.md` | Observability stack guide |
| `services/logging-client/STRUCTURED_LOGGING.md` | Logging patterns and examples |
| `ops/README.md` | Observability provisioning and scripts |
| `ops/opensearch/TRACING_QUERIES.md` | OpenSearch query examples |
| `e2e-tests/README.md` | E2E test setup and usage |

## 13. Getting Help

**Unclear patterns?** Check existing code:
- Controllers: `grep -r "@RestController" services/*/src/main/java`
- Listeners: `grep -r "@RabbitListener" services/*/src/main/java`
- Event DTOs: `ls services/event-contracts/src/main/java/nik/kalomiris/events/dtos/`

**Missing templates?** Ask for:
- PR body template
- Migration checklist template
- Test harness examples
- Implementation plan template

---

*Keep changes small, preserve conventions, and test locally before opening PRs.*
