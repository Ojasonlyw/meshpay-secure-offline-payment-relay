package com.demo.upimesh.exception;

public class InsufficientBalanceException extends DomainException {
    public InsufficientBalanceException() { this("Insufficient balance."); }
    public InsufficientBalanceException(String detail) { this(detail, null); }
    public InsufficientBalanceException(String detail, Throwable cause) {
        super(422, "INSUFFICIENT_BALANCE", "Insufficient balance.", detail, cause);
    }
}

