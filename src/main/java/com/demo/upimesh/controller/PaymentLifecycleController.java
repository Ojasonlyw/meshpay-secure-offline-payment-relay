package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.PaymentLifecycleResponse;
import com.demo.upimesh.service.PaymentQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentLifecycleController {
    private final PaymentQueryService payments;

    public PaymentLifecycleController(PaymentQueryService payments) {
        this.payments = payments;
    }

    @GetMapping
    public List<PaymentLifecycleResponse> recent() { return payments.recent(); }

    @GetMapping("/{paymentId}")
    public PaymentLifecycleResponse find(@PathVariable UUID paymentId) { return payments.find(paymentId); }
}
