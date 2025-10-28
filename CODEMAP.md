# CODEMAP

This file summarizes the microservices in the repository and their primary responsibilities and entry points.

- services/order-service
  - Purpose: Accepts orders, persists them, and emits OrderCreated events.
  - Entry points: `OrderServiceApplication` (main), `OrderController` (REST), `OrderService` (business logic), listeners: `InventoryEventListener`.

- services/inventory-service
  - Purpose: Maintain inventory counts, reserve/commit/release stock, consume product and order events.
  - Entry points: `InventoryServiceApplication` (main), `InventoryController` (REST), `InventoryService` (business logic), listeners: `OrderEventListener`, `ProductEventListener`.

- services/product-service
  - Purpose: Manage product catalog, images and categories; publish ProductCreated events.
  - Entry points: `ProductServiceApplication` (main), `ProductController` (REST), `ProductService` (business logic), `RabbitMQConfig` (messaging).

- services/review-service
  - Purpose: Store and manage product reviews, provide voting endpoints, publish lightweight logs.
  - Entry points: `ReviewServiceApplication` (main), `ReviewController` (REST), `ReviewService` (business logic).

- services/logging-service
  - Purpose: Lightweight consumer that pretty-prints structured logs (Kafka) for local/dev runs.
  - Entry points: `LoggingServiceApplication` (main), `LogListener` (Kafka consumer).

- services/logging-client
  - Purpose: Shared library used by services to publish structured logs to Kafka.
  - Entry points: `LogPublisher`, `LogMessage`.

- services/event-contracts
  - Purpose: Shared DTOs (integration contracts) used across services for events (OrderEvent, InventoryReservedEvent, ProductCreatedEvent, etc.).

Notes:
- Keep the repository-wide logging configuration under each service's `src/main/resources` (e.g., `application.properties`).
- To generate Javadoc for the modules, run Maven's javadoc plugin (see below).

"Try it" - generate Javadoc (optional):

Run from repository root (requires Maven):

```bash
mvn -DskipTests javadoc:javadoc
```

This will generate module-specific Javadoc under each module's `target/site/apidocs` directory.

If you'd like, I can also open a branch/PR containing only the comment changes for review, or continue annotating any additional modules you care about.
