package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.TransactionResponse;
import com.demo.upimesh.service.TransactionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> listTransactions() {
        return transactions.listRecentTransactions();
    }
}
