package nik.kalomiris.order_service.listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import nik.kalomiris.order_service.domain.Order;
import nik.kalomiris.order_service.domain.OrderStatus;
import nik.kalomiris.events.dtos.InventoryReservedEvent;
import nik.kalomiris.events.dtos.InventoryReservationFailedEvent;
import nik.kalomiris.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;

class InventoryEventListenerTest {

    @Test
    void handleInventoryReserved_updatesOrderToReserved() {
        OrderRepository repo = mock(OrderRepository.class);
        InventoryEventListener listener = new InventoryEventListener(repo);

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("order-1");
        order.setStatus(OrderStatus.CREATED);

        when(repo.findByOrderNumber("order-1")).thenReturn(Optional.of(order));
        when(repo.findById(1L)).thenReturn(Optional.of(order));

        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setOrderNumber("order-1");

        listener.handleInventoryReserved(event);

        // saved once with updated status
        verify(repo, times(1)).save(order);
    }

    @Test
    void handleInventoryReservationFailed_updatesOrderToFailed() {
        OrderRepository repo = mock(OrderRepository.class);
        InventoryEventListener listener = new InventoryEventListener(repo);

        Order order = new Order();
        order.setId(2L);
        order.setOrderNumber("order-2");
        order.setStatus(OrderStatus.CREATED);

        when(repo.findByOrderNumber("order-2")).thenReturn(Optional.of(order));
        when(repo.findById(2L)).thenReturn(Optional.of(order));

        InventoryReservationFailedEvent event = new InventoryReservationFailedEvent();
        event.setOrderNumber("order-2");
        event.setReason("insufficient stock");

        listener.handleInventoryReservationFailed(event);

        verify(repo, times(1)).save(order);
    }
}
