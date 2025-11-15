package nik.kalomiris.product_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import nik.kalomiris.product_service.config.TestMessagingConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
