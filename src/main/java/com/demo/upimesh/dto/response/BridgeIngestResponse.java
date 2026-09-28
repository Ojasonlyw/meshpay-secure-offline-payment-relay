package com.demo.upimesh.dto.response;

public record BridgeIngestResponse(String outcome, String packetHash, String reason, Long transactionId) {}
