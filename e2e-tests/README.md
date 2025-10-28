E2E tests for microservices-project

This module contains a JUnit/Testcontainers based end-to-end test that:

- Starts the repository's `docker-compose.yml` services (product, inventory, order, postgres, rabbitmq, kafka).
- Creates a category and product via the `product-service` REST API.
- Sets inventory for the created product directly in the `inventorydb` Postgres database so the test is repeatable.
- Places an order via the `order-service` REST API.
- Verifies inventory was committed by querying the `inventory-service` REST API.

Prerequisites
- Docker Engine and docker-compose installed and running on the host
- Java 11+ and Maven

Run locally

Manually start the docker-compose services.

From the repo root:

```bash
cd e2e-tests
SKIP_COMPOSE=false mvn -f e2e-tests/pom.xml test -DskipTests=false -DtrimStackTrace=false
```

Notes
- The test uses the project-level `docker-compose.yml` (absolute path is set in the test to the current workspace path). If you move the workspace, update the compose file path in `E2ETest.java` or set up your own wrapper.
- The test directly updates the `inventory` table in the `inventorydb` database so the `productId` used by the `order-service` maps to an inventory record for deterministic behavior. This keeps the test repeatable without manual setup.
- Depending on machine speed, container startup may take several minutes.

CI
- You can run this in CI if the runner supports Docker (GitHub Actions with runners that provide Docker). Keep in mind the job needs privileged access to run Docker.
