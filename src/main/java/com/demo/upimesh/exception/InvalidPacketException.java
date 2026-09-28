package com.demo.upimesh.exception;

public class InvalidPacketException extends DomainException {
    public InvalidPacketException() { this("Invalid payment packet."); }
    public InvalidPacketException(String detail) { this(detail, null); }
    public InvalidPacketException(String detail, Throwable cause) {
        super(400, "INVALID_PACKET", "Invalid payment packet.", detail, cause);
    }
}

