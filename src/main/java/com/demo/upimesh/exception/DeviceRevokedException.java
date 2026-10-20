package com.demo.upimesh.exception;

public class DeviceRevokedException extends DomainException {
    public DeviceRevokedException() {
        super(400, "DEVICE_REVOKED", "Signing device or key is no longer active.", "Settlement-time device/key check failed", null);
    }
}
