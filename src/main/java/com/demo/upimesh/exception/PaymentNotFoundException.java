package com.demo.upimesh.exception;

public class PaymentNotFoundException extends DomainException {
    public PaymentNotFoundException() { this("Payment not found."); }
    public PaymentNotFoundException(String detail) { this(detail, null); }
    public PaymentNotFoundException(String detail, Throwable cause) {
        super(404, "PAYMENT_NOT_FOUND", "Payment not found.", detail, cause);
    }
}

