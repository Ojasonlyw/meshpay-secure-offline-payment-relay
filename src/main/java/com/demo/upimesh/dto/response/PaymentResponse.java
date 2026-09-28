package com.demo.upimesh.dto.response;

public record PaymentResponse(String packetId, String ciphertextPreview, int ttl, String injectedAt) {}
