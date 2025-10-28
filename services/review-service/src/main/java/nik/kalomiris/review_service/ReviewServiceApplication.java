package nik.kalomiris.review_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "nik.kalomiris")
public class ReviewServiceApplication {

	/**
	 * Main entrypoint for the Review Service which stores and manages product
	 * reviews. Provides REST endpoints used by UI/tests and emits structured
	 * logs via the logging client.
	 */

	public static void main(String[] args) {
		SpringApplication.run(ReviewServiceApplication.class, args);
	}

}
