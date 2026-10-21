package com.demo.upimesh.service;

import com.demo.upimesh.dto.response.PaymentLifecycleResponse;
import com.demo.upimesh.exception.PaymentNotFoundException;
import com.demo.upimesh.model.Payment;
import com.demo.upimesh.repository.PaymentRepository;
import com.demo.upimesh.repository.TransactionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentQueryService {
    private final PaymentRepository payments;
    private final TransactionRepository transactions;

    public PaymentQueryService(PaymentRepository payments, TransactionRepository transactions) {
        this.payments = payments;
        this.transactions = transactions;
    }

    public List<PaymentLifecycleResponse> recent() {
        List<Payment> recent = payments.findTop20ByOrderByReceivedAtDescIdDesc();
        if (recent.isEmpty()) return List.of();
        var ids = transactions.findIdsByPaymentIds(recent.stream().map(Payment::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(TransactionRepository.PaymentTransaction::getPaymentId,
                        TransactionRepository.PaymentTransaction::getTransactionId));
        return recent.stream().map(p -> response(p, ids.get(p.getId()))).toList();
    }

    public PaymentLifecycleResponse find(UUID paymentId) {
        return response(payments.findByPaymentId(paymentId).orElseThrow(PaymentNotFoundException::new));
    }

    private PaymentLifecycleResponse response(Payment p) {
        return response(p, transactions.findIdByPaymentId(p.getId()).orElse(null));
    }

    private PaymentLifecycleResponse response(Payment p, Long transactionId) {
        return new PaymentLifecycleResponse(p.getPaymentId(), p.getPacketHash(), p.getSenderVpa(),
                p.getReceiverVpa(), p.getAmount(), p.getStatus().name(), p.getSignedAt(), p.getReceivedAt(),
                p.getSettledAt(), p.getFailureCode(), p.getFailureMessage(), p.getBridgeNodeId(),
                p.getHopCount(), transactionId);
    }
}
