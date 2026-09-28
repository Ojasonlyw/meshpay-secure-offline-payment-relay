package com.demo.upimesh.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PacketRouteResponse(String packetId, UUID paymentId, String sourceDeviceId,
        String destinationDeviceId, int hopNumber, Instant receivedAt, Instant forwardedAt,
        int ttlAfterHop) {}
