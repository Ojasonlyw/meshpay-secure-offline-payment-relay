package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.ServerKeyResponse;
import com.demo.upimesh.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/server-key")
    public ServerKeyResponse publicKey() {
        return payments.publicKey();
    }

}
