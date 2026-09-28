package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.TransactionResponse;
import com.demo.upimesh.mapper.TransactionMapper;
import com.demo.upimesh.repository.TransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;

    public TransactionService(TransactionRepository transactions, TransactionMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TransactionResponse> listRecentTransactions() {
        return transactions.findTop20ByOrderByIdDesc().stream().map(mapper::toResponse).toList();
    }
}
