package nik.kalomiris.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for HTTP clients used to communicate with other services.
 */
@Configuration
public class WebClientConfig {

    /**
     * Provides a RestTemplate bean for making synchronous HTTP calls.
     * Used by ProductServiceClient to fetch product information.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
