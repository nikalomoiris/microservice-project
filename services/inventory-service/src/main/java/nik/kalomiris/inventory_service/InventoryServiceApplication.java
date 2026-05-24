package nik.kalomiris.inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "nik.kalomiris")
/**
 * Main entry point for the Inventory Service application.
 *
 * Bootstraps Spring and configures component scanning under `nik.kalomiris` so
 * shared libraries and cross-service components are discovered.
 *
 * Quick flow reference:
 * RabbitMQ order events -> OrderEventListener -> InventoryService
 * -> InventoryRepository updates (reserve/commit/release)
 * -> RabbitMQ inventory outcome events back to order-service.
 *
 * Observability hooks in the flow:
 * - structured logs via LogPublisher
 */
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

}
