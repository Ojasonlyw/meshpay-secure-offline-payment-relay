package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.response.AccountResponse;
import com.demo.upimesh.model.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getVpa(), account.getHolderName(),
                account.getBalance(), account.getVersion());
    }
}
