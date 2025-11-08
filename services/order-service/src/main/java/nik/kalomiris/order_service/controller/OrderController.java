package nik.kalomiris.order_service.controller;

import nik.kalomiris.order_service.dto.OrderRequest;
import nik.kalomiris.order_service.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
/**
 * REST controller exposing order-related endpoints.
 *
 * Currently exposes a single POST endpoint to create orders. The controller
 * delegates business logic to {@code OrderService} and returns a simple
 * confirmation message. Keep controllers thin — no business logic here.
 */
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createOrder(@RequestBody OrderRequest orderRequest) {
        // Delegate to service which persists the order and emits integration events
        orderService.createOrder(orderRequest);
        return "Order Created Successfully";
    }

    @PostMapping("/confirm/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    public String confirmOrder(@PathVariable String orderNumber) {
        orderService.confirmOrder(orderNumber);
        return "Order Confirmed Successfully";
    }
}
