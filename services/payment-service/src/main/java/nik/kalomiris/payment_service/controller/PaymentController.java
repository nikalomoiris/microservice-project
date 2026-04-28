package nik.kalomiris.payment_service.controller;

import nik.kalomiris.payment_service.dto.PaymentRequest;
import nik.kalomiris.payment_service.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment operations.
 * Provides endpoints for creating and managing payments.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a new payment for an order.
     *
     * @param paymentRequest the payment request containing orderId and amount
     * @return ResponseEntity with HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<Void> createPayment(@RequestBody PaymentRequest paymentRequest) {
        paymentService.createPayment(paymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

