package nik.kalomiris.order_service.repository;

import nik.kalomiris.order_service.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
	Optional<Order> findByOrderNumber(String orderNumber);
}
