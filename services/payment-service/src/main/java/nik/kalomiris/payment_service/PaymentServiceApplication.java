package nik.kalomiris.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "nik.kalomiris" })
/**
 * Main entry point for the Payment Service.
 *
 * Boots the Spring context and sets up controllers, repositories and
 * message listeners that manage payment processing and transactions.
 */
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
