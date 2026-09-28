package com.demo.upimesh.exception;

public class ExpiredPacketException extends DomainException {
    public ExpiredPacketException() { this("Payment packet has expired."); }
    public ExpiredPacketException(String detail) { this(detail, null); }
    public ExpiredPacketException(String detail, Throwable cause) {
        super(400, "PACKET_EXPIRED", "Payment packet has expired.", detail, cause);
    }
}

