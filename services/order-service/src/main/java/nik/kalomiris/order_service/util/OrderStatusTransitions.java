package nik.kalomiris.order_service.util;

import nik.kalomiris.order_service.domain.OrderStatus;

public class OrderStatusTransitions {

    public static boolean canTransitionTo(OrderStatus current, OrderStatus target) {
        // Helper that encodes valid state transitions for the Order state machine.
        return switch (current) {
            case CREATED -> target == OrderStatus.RESERVED || target == OrderStatus.RESERVATION_FAILED || target == OrderStatus.PARTIALLY_RESERVED;
            case PARTIALLY_RESERVED -> target == OrderStatus.RESERVED || target == OrderStatus.RESERVATION_FAILED;
            case RESERVED -> target == OrderStatus.COMMITTED || target == OrderStatus.CANCELLED;
            case COMMITTED -> target == OrderStatus.CONFIRMED;
            case CONFIRMED -> target == OrderStatus.SHIPPED || target == OrderStatus.COMPLETED;
            default -> false;
        };
    }

}
