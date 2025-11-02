package nik.kalomiris.order_service.util;

import nik.kalomiris.order_service.domain.OrderStatus;

public class OrderStatusTransitions {

    public static boolean canTransitionTo(OrderStatus current, OrderStatus target) {
        // Helper that encodes valid state transitions for the Order state machine.
        return switch (current) {
            case CREATED -> target == OrderStatus.RESERVED || target == OrderStatus.RESERVATION_FAILED || target == OrderStatus.PARTIALLY_RESERVED;
            case PARTIALLY_RESERVED -> target == OrderStatus.RESERVED || target == OrderStatus.RESERVATION_FAILED;
            // After items are reserved we can confirm the order (customer/payment) or cancel it
            case RESERVED -> target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED;
            // Once confirmed, inventory should be committed (by inventory service) -> COMMITTED
            // Confirmed orders may also progress to shipment/completion in corner cases
            case CONFIRMED -> target == OrderStatus.COMMITTED || target == OrderStatus.SHIPPED || target == OrderStatus.COMPLETED;
            // After commit, the order can move to shipped or completed
            case COMMITTED -> target == OrderStatus.SHIPPED || target == OrderStatus.COMPLETED;
            default -> false;
        };
    }

}
