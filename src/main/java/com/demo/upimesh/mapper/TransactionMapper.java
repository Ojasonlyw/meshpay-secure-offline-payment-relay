package com.demo.upimesh.mapper;

import com.demo.upimesh.dto.response.TransactionResponse;
import com.demo.upimesh.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {
    public TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getPacketHash(), tx.getSenderVpa(),
                tx.getReceiverVpa(), tx.getAmount(), tx.getSignedAt(), tx.getSettledAt(),
                tx.getBridgeNodeId(), tx.getHopCount(), tx.getStatus().name(), tx.getPayment().getPaymentId());
    }
}
