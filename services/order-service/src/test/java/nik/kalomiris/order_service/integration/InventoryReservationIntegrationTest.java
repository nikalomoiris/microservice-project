package nik.kalomiris.order_service.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration test scaffold for end-to-end order -> inventory -> order-status flow.
 *
 * This test is disabled by default because it requires Docker/RabbitMQ/Postgres and
 * the application's docker-compose stack to be available. Run it locally when you
 * want to perform an end-to-end verification.
 */
@Disabled("Requires Docker/RabbitMQ/Postgres — enable for local integration testing")
public class InventoryReservationIntegrationTest {

    @Test
    void endToEnd_reservationUpdatesOrderStatus() {
        // See README / docs for instructions to run the docker-compose stack and
        // execute this test locally. This scaffold is intentionally simple — it
        // should start the stack, call the order endpoint, then verify order status.
    }
}
