package com.demo.upimesh.dto.response;

import java.util.List;

public record MeshFlushResponse(int uploadsAttempted, List<Result> results) {
    public record Result(String bridgeNode, String packetId, String outcome, String reason,
                         long transactionId, int hopCount) {}
}
