package com.demo.upimesh.controller;

import com.demo.upimesh.dto.response.AccountResponse;
import com.demo.upimesh.service.AccountService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/accounts")
    public List<AccountResponse> listAccounts() {
        return accounts.listAccounts();
    }
}
