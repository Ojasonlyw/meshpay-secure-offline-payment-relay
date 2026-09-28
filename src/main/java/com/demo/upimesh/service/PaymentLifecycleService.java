package com.demo.upimesh.service;

import com.demo.upimesh.exception.*;
import com.demo.upimesh.model.Payment;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.PaymentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLifecycleService {
    private static final Set<String> RETRYABLE = Set.of(
            "ACCOUNT_NOT_FOUND", "SETTLEMENT_FAILED", "PAYMENT_PROCESSING_FAILED");
    private final PaymentRepository payments;
    private final JdbcTemplate jdbc;

    public PaymentLifecycleService(PaymentRepository payments, JdbcTemplate jdbc) {
        this.payments = payments;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment register(PaymentInstruction instruction, String hash, String bridge, int hops) {
        UUID identity = UUID.fromString(instruction.getPaymentId());
        UUID nonce = UUID.fromString(instruction.getNonce());
        jdbc.update("""
                INSERT INTO payments(payment_id,packet_hash,sender_vpa,receiver_vpa,amount,status,
                    nonce,signed_at,received_at,bridge_node_id,hop_count,version)
                VALUES (?,?,?,?,?,'PENDING',?,?,?, ?,?,0) ON CONFLICT DO NOTHING
                """, identity, hash, instruction.getSenderVpa(), instruction.getReceiverVpa(),
                instruction.getAmount(), nonce, Timestamp.from(Instant.ofEpochMilli(instruction.getSignedAt())),
                Timestamp.from(Instant.now()), bridge, hops);
        Payment payment = payments.findByPacketHash(hash).orElseThrow(DuplicatePaymentException::new);
        if (!identity.equals(payment.getPaymentId()) || !nonce.equals(payment.getNonce()) || !canSettle(payment)) {
            throw new DuplicatePaymentException();
        }
        return payment;
    }

    public static boolean canSettle(Payment payment) {
        return payment.getStatus() == Payment.Status.PENDING
                || (payment.getStatus() == Payment.Status.FAILED && RETRYABLE.contains(payment.getFailureCode()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long id, Long observedVersion, DomainException failure) {
        Payment payment = payments.lockById(id).orElseThrow(PaymentNotFoundException::new);
        // A newer attempt or terminal result must never be overwritten by an older failure.
        if (!Objects.equals(payment.getVersion(), observedVersion) || !canSettle(payment)) return;
        payment.setStatus(failure instanceof ExpiredPacketException ? Payment.Status.EXPIRED
                : failure instanceof InvalidSignatureException ? Payment.Status.REJECTED : Payment.Status.FAILED);
        payment.setFailureCode(failure.getErrorCode());
        payment.setFailureMessage(failure.getPublicMessage());
    }
}
