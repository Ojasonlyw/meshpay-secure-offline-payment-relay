package com.demo.upimesh.exception;

public abstract class DomainException extends RuntimeException {
    private final int status;
    private final String errorCode;
    private final String publicMessage;

    protected DomainException(int status, String code, String publicMessage, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.errorCode = code;
        this.publicMessage = publicMessage;
    }
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getPublicMessage() { return publicMessage; }
}

