package nik.kalomiris.payment_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing support for payment entities.
 */
@Configuration
@EnableJpaAuditing
public class PaymentAuditConfig {

}
