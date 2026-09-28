package com.demo.upimesh.dto.response;

import java.time.Instant;

public record DashboardNetworkStatsResponse(long totalDevices, long activeDevices,
        long trustedDevices, long revokedDevices, long bridgeDevices, long offlineDevices,
        long activeConnections, long packetsRouted, double averageHopCount,
        Instant latestRouteTimestamp, String mostActiveBridgeDevice) {}
