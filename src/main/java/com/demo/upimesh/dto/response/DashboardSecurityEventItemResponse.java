package com.demo.upimesh.dto.response;

import java.time.Instant;

public record DashboardSecurityEventItemResponse(String eventType, String severity, String message,
        String deviceId, String paymentId, Instant occurredAt) {}
