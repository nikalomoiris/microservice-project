package nik.kalomiris.inventory_service;

import nik.kalomiris.inventory_service.config.TestRabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRabbitMQConfig.class)
class InventoryServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
