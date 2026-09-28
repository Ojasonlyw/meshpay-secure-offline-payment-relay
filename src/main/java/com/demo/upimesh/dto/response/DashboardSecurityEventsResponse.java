package com.demo.upimesh.dto.response;

import java.util.List;

public record DashboardSecurityEventsResponse(long invalidSignatureEvents,
        long rejectedDeviceAuthorization, long failedSignatureVerification,
        List<DashboardSecurityEventItemResponse> items) {}
