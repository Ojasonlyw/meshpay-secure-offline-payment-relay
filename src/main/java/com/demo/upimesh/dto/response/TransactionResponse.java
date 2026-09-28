package com.demo.upimesh.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, String packetHash, String senderVpa, String receiverVpa,
        BigDecimal amount, Instant signedAt, Instant settledAt, String bridgeNodeId,
        int hopCount, String status, java.util.UUID paymentId) {}
