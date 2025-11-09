<!-- .github/copilot-instructions.md - guidance for AI coding agents -->
# Microservices Project — Copilot Instructions (quick reference)

Purpose: Give an AI coding agent the minimal, actionable knowledge to be productive in this monorepo. Keep edits small, preserve existing conventions, and point to concrete files/examples.

1) Big picture (why and where)
- Monorepo of Spring Boot 3.5.7 microservices (Java 21). Services live under `services/` and include: `product-service`, `inventory-service`, `order-service`, `review-service`, `logging-service`, and a `logging-client` library.
- Infra for local dev is provided in `docker-compose.yml` (Postgres, RabbitMQ, Zookeeper, Kafka). Services communicate via REST + RabbitMQ events; structured logs flow through Kafka.

2) Key locations (start here)
- Root: `mvn clean install` builds all modules. See `CODEMAP.md` for responsibilities per service.
- Compose stack: `docker-compose.yml` (root) — mounts `services/create-databases.sh` into Postgres.
- Event contracts: `services/event-contracts` (shared DTOs used by multiple services).
- Messaging topology and config examples:
  - RabbitMQ config classes: `services/*/src/main/java/**/config/RabbitMQConfig.java` (e.g., `inventory-service`).
  - Listeners: `services/*/src/main/java/**/listeners/*` (e.g., `OrderEventListener`, `InventoryEventListener`).
- Entrypoints / mains: `services/*/src/main/java/**/*Application.java` (e.g., `ProductServiceApplication`).
- Runbook & diagrams: `docs/service-topology.md`, `docs/message-flow.md` (for message shapes and run steps).

3) Concrete build & run commands
- Build all modules (from repo root):
  ```bash
  mvn clean install
  ```
- Start infra + services (docker-compose local dev):
  ```bash
  docker-compose up -d
  ```
- Run a single service locally (module-level Maven wrapper if present):
  ```bash
  # from service dir
  cd services/product-service && ./mvnw spring-boot:run

  # or from repo root (multi-module):
  mvn -pl services/product-service -am spring-boot:run
  ```
- Initialize DBs: `services/create-databases.sh` is mounted into Postgres and runs at container init; see `docker-compose.yml`.
- Run e2e tests (maven wrapper in e2e-tests):
  ```bash
  mvn -f e2e-tests/pom.xml test
  ```

4) Network & messaging assumptions
- RabbitMQ exchanges and routing keys are canonical and must be preserved when changing shapes. Key exchanges:
  - `product-exchange` — routing key `product.created`
  - `order-exchange` — routing keys `order.created`, `order.inventory.*` (see `docs/service-topology.md`)
- Kafka used for logs; topic(s) used by `logging-client` / `logging-service` (see `services/logging-client/STRUCTURED_LOGGING.md`).
- Services use the compose container hostnames (e.g., `postgres-service`, `rabbitmq-service`, `kafka-service`) in `docker-compose.yml` — prefer those hostnames in compose/dev.

5) Project-specific conventions
- Packaging: some services use package names with underscores (e.g., `nik.kalomiris.product_service`); follow existing package patterns and avoid global renames.
- Events & contracts: keep event DTOs in `services/event-contracts` and update all consumers/producers together; follow the PR checklist (see `docs/PR_CHECKLIST.md`).
- Idempotency: listeners must be idempotent (RabbitMQ redeliveries expected). When modifying listeners, add idempotency tests.
- Logging: use `logging-client` library for structured logs to Kafka rather than ad-hoc console prints. Use the `LogMessage.Builder()` pattern for structured logs with metadata, tracing, and context (see `services/logging-client/STRUCTURED_LOGGING.md`).

6) Common tasks for an agent (examples)
- Add new event: update `services/event-contracts` → update producer (e.g., `order-service`) → update consumer(s) (`inventory-service`) → update `docs/message-flow.md` and `docs/service-topology.md` → update `docker-compose` only if you add infra.
- Add endpoint to product-service: edit controller `services/product-service/src/main/java/.../ProductController.java`, service `ProductService`, DTOs in `event-contracts` if publishing events.
- Fix RabbitMQ constant mismatch: check `services/inventory-service/src/main/java/.../RabbitMQConfig.java` and `services/order-service` listeners; update both sides and run smoke flow locally.

7) What to avoid / preserve
- Avoid breaking changes to event shapes without a rollout plan — consult `docs/PR_CHECKLIST.md` and follow the deployment order (deploy consumers first or ensure backward compatibility).
- Do not hardcode infrastructure hostnames/ports; use `docker-compose.yml` for local dev and config in `src/main/resources/application.properties` for service-level defaults.

8) Tests, CI, and missing pieces
- Tests: unit tests are in each service module (Maven). E2E tests live in `e2e-tests/` (Maven). There is no repository-level CI visible; keep changes minimal and add CI that runs `mvn -T1C clean install && mvn -f e2e-tests/pom.xml test` if requested.
- Migrations: DB init script is `services/create-databases.sh`. If you add DB migrations, document them in PR and update the PR checklist.

9) Useful files to reference quickly
- `docker-compose.yml` — infra + service ports and env mapping
- `CODEMAP.md` — quick summary of each service responsibilities
- `docs/service-topology.md` — message flows, exchanges, routing keys
- `docs/PR_CHECKLIST.md` — rollout & PR validation checklist for event-driven changes
- `services/*/HELP.md` — service-specific developer notes (package name quirks, mvnw usage)

10) Interaction style for PRs
- Keep changes small & self-contained. PR should include:
  - Short summary and concrete files changed
  - Manual smoke test steps (curl commands) and expected flow
  - If events are changed, include sample JSON and deployment order per `docs/PR_CHECKLIST.md`

If anything above is unclear or you want templates (PR body, migration checklist, test harness), tell me which and I'll add them as small, follow-up files.

-- End of instructions --
