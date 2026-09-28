package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.ReconciliationResponse;
import com.demo.upimesh.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {
    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping("/api/reconciliation")
    public ReconciliationResponse reconcile() {
        return reconciliation.reconcile();
    }
}
