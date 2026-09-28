package com.demo.upimesh.exception;

public class DeviceNotFoundException extends DomainException {
    public DeviceNotFoundException() {
        super(404, "DEVICE_NOT_FOUND", "Device not found.", "Device not found", null);
    }
}
