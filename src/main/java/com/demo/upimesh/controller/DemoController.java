package com.demo.upimesh.controller;

import com.demo.upimesh.dto.request.DemoSendRequest;
import com.demo.upimesh.dto.response.MeshResetResponse;
import com.demo.upimesh.dto.response.PaymentResponse;
import com.demo.upimesh.service.MeshService;
import com.demo.upimesh.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Explicit local helpers; even mixed postgres/dev profiles cannot enable server-side signing/reset. */
@RestController
@RequestMapping("/api")
@Profile("(dev | demo | test) & !postgres")
public class DemoController {
    private final PaymentService payments;
    private final MeshService mesh;

    public DemoController(PaymentService payments, MeshService mesh) {
        this.payments = payments;
        this.mesh = mesh;
    }

    @PostMapping("/demo/send")
    public PaymentResponse send(@Valid @RequestBody DemoSendRequest request) throws Exception {
        return payments.sendDemo(request);
    }

    @PostMapping("/mesh/reset")
    public MeshResetResponse reset() { return mesh.reset(); }
}
