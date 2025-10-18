package nik.kalomiris.order_service.repository;

import nik.kalomiris.order_service.domain.OrderLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, Long> {
}
