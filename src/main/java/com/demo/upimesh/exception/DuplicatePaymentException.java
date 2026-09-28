package com.demo.upimesh.exception;

public class DuplicatePaymentException extends DomainException {
    public DuplicatePaymentException() { this("Payment has already been processed."); }
    public DuplicatePaymentException(String detail) { this(detail, null); }
    public DuplicatePaymentException(String detail, Throwable cause) {
        super(409, "DUPLICATE_PAYMENT", "Payment has already been processed.", detail, cause);
    }
}

