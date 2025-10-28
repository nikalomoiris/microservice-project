package nik.kalomiris.product_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"nik.kalomiris"})
	/**
	 * Main entry point for the Product Service.
	 *
	 * Boots the Spring context and sets up controllers, repositories and
	 * message listeners that manage product lifecycle and images.
	 */
public class ProductServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductServiceApplication.class, args);
	}

}
