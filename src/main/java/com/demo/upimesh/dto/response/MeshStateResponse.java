package com.demo.upimesh.dto.response;

import java.util.List;

public record MeshStateResponse(List<Device> devices, int idempotencyCacheSize, int activeConnections) {
    public record Device(String deviceId, boolean hasInternet, int packetCount, List<String> packetIds) {}
}
