package nik.kalomiris.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "nik.kalomiris")
/**
 * Main entry point for the Order Service Spring Boot application.
 *
 * This class bootstraps the Spring context and triggers component scanning
 * under the base package `nik.kalomiris` so other microservice modules
 * (shared libraries, clients, etc.) are discovered.
 *
 * Quick flow reference:
 * OrderController -> OrderService -> (OrderRepository + ProductServiceClient)
 * -> RabbitMQ OrderEvent publish -> inventory-service processing
 * -> InventoryEventListener consumes inventory events -> Order status update.
 *
 * Observability hooks in the flow:
 * - structured logs via LogPublisher
 */
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
