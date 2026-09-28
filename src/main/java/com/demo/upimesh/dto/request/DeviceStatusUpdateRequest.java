package com.demo.upimesh.dto.request;

import com.demo.upimesh.model.Device;

public record DeviceStatusUpdateRequest(Device.Status status, Device.TrustStatus trustStatus) {}
