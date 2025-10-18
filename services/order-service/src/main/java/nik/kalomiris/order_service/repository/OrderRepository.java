package nik.kalomiris.order_service.repository;

import nik.kalomiris.order_service.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
