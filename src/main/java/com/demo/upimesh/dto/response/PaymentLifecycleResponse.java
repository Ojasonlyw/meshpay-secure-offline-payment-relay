package com.demo.upimesh.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentLifecycleResponse(UUID paymentId, String packetHash, String senderVpa,
        String receiverVpa, BigDecimal amount, String status, Instant signedAt, Instant receivedAt,
        Instant settledAt, String failureCode, String failureMessage, String bridgeNodeId,
        int hopCount, Long transactionId) {}
