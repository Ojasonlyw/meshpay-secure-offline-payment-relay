package com.demo.upimesh.dto.response;

import java.time.Instant;

public record DeviceResponse(String deviceId, String userVpa, String deviceName, String status,
        boolean internetCapability, String trustStatus, String keyFingerprint,
        Instant registeredAt, Instant lastSeenAt) {}
