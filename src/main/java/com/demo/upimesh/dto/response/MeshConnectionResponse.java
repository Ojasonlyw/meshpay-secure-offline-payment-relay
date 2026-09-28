package com.demo.upimesh.dto.response;

import java.time.Instant;

public record MeshConnectionResponse(long id, String sourceDeviceId, String targetDeviceId,
        String status, String linkType, Instant createdAt, Instant lastSeenAt) {}
