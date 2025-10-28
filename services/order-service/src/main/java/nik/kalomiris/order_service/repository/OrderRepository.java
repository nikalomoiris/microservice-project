package nik.kalomiris.order_service.repository;

import nik.kalomiris.order_service.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
	/**
	 * Find an Order by its external order number (UUID-style string).
	 * Returns an empty Optional when no matching order is found.
	 */
	Optional<Order> findByOrderNumber(String orderNumber);
}
