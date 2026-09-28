package com.demo.upimesh.exception;

public class SettlementException extends DomainException {
    public SettlementException() { this("Payment settlement failed."); }
    public SettlementException(String detail) { this(detail, null); }
    public SettlementException(String detail, Throwable cause) {
        super(500, "SETTLEMENT_FAILED", "Payment settlement failed.", detail, cause);
    }
}

