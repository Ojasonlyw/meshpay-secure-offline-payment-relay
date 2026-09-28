package com.demo.upimesh.exception;

public class InvalidSignatureException extends DomainException {
    public InvalidSignatureException() { this("Payment packet could not be verified."); }
    public InvalidSignatureException(String detail) { this(detail, null); }
    public InvalidSignatureException(String detail, Throwable cause) {
        super(400, "INVALID_SIGNATURE", "Payment packet could not be verified.", detail, cause);
    }
}

