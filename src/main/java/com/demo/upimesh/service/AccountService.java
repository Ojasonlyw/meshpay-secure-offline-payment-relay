package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.AccountResponse;
import com.demo.upimesh.mapper.AccountMapper;
import com.demo.upimesh.repository.AccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AccountMapper mapper;

    public AccountService(AccountRepository accounts, AccountMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    public List<AccountResponse> listAccounts() {
        return accounts.findAll().stream().map(mapper::toResponse).toList();
    }
}
