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
 */
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
