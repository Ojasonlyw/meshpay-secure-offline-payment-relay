package com.demo.upimesh.exception;

public class DeviceConflictException extends DomainException {
    public DeviceConflictException(String detail) {
        super(409, "DEVICE_CONFLICT", "Device registration conflicts with an existing device.", detail, null);
    }
}
