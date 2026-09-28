package com.demo.upimesh.exception;

public class AccountNotFoundException extends DomainException {
    public AccountNotFoundException() { this("Account not found."); }
    public AccountNotFoundException(String detail) { this(detail, null); }
    public AccountNotFoundException(String detail, Throwable cause) {
        super(404, "ACCOUNT_NOT_FOUND", "Account not found.", detail, cause);
    }
}

