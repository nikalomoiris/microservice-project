package nik.kalomiris.review_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "nik.kalomiris")
/**
 * Main entry point for the Review Service which stores and manages product
 * reviews and evaluation metadata.
 *
 * Quick flow reference:
 * ReviewController -> ReviewService -> ReviewRepository/Evaluation components
 * -> optional product-service reads for product context.
 *
 * Observability hooks in the flow:
 * - structured logs via LogPublisher
 * - tracing via Micrometer Tracer
 */
public class ReviewServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewServiceApplication.class, args);
	}

}
