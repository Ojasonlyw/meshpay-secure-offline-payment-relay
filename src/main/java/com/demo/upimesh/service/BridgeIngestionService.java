package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.exception.*;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.Payment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    private final HybridCryptoService crypto;
    private final IdempotencyService idempotency;
    private final SettlementService settlement;
    private final Validator validator;
    private final PaymentLifecycleService lifecycle;
    private final DeviceSignatureService signatures;
    private final MeshTopologyService topology;

    public BridgeIngestionService(HybridCryptoService crypto, IdempotencyService idempotency,
                                  SettlementService settlement, Validator validator, PaymentLifecycleService lifecycle,
                                  DeviceSignatureService signatures, MeshTopologyService topology,
                                  @Value("${upi.mesh.packet-max-age-seconds:86400}") long maxAgeSeconds,
                                  @Value("${mesh.max-ttl:10}") int maxTtl,
                                  @Value("${mesh.max-hop-count:10}") int maxHopCount,
                                  @Value("${mesh.max-payload-size:16384}") int maxPayloadSize) {
        this.crypto = crypto;
        this.idempotency = idempotency;
        this.settlement = settlement;
        this.validator = validator;
        this.lifecycle = lifecycle;
        this.signatures = signatures;
        this.topology = topology;
        this.maxAgeSeconds = maxAgeSeconds;
        this.maxTtl = maxTtl;
        this.maxHopCount = maxHopCount;
        this.maxPayloadSize = maxPayloadSize;
    }

    private final long maxAgeSeconds;
    private final int maxTtl;
    private final int maxHopCount;
    private final int maxPayloadSize;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        String packetHash = "?";
        boolean claimed = false;
        String attemptToken = null;
        boolean finalized = false;
        Payment payment = null;
        try {
            if (bridgeNodeId == null || !bridgeNodeId.matches("[A-Za-z0-9._:-]{1,64}"))
                throw new InvalidPacketException("Invalid bridge ID");
            String packetError = validatePacketEnvelope(packet, hopCount);
            if (packetError != null) {
                throw new InvalidPacketException(packetError);
            }

            packetHash = crypto.hashCiphertext(packet.getCiphertext());

            IdempotencyService.ClaimResult claim = null;
            boolean redisAvailable = true;
            try {
                claim = idempotency.claimProcessing(packetHash);
                claimed = claim.claimed();
                attemptToken = claim.token();
            } catch (org.springframework.dao.DataAccessException unavailable) {
                redisAvailable = false;
                log.warn("Replay cache unavailable for packetHash={}", packetHash);
            }

            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                throw new InvalidSignatureException("Decryption failed", e);
            }

            String instructionError = validateInstruction(instruction);
            if (instructionError != null) {
                throw new InvalidPacketException(instructionError);
            }

            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if (ageSeconds > maxAgeSeconds) {
                log.warn("Packet {} too old ({}s), rejected",
                        packetHash.substring(0, 12) + "...", ageSeconds);
                throw new ExpiredPacketException();
            }
            if (ageSeconds < -300) {
                throw new InvalidPacketException("Future dated packet");
            }

            DeviceSignatureService.Verification verification =
                    signatures.verify(instruction);
            if (!verification.valid()) throw new InvalidSignatureException(verification.failureCode());

            var existing = lifecycle.findExisting(instruction, packetHash);
            if (existing.isPresent() && !PaymentLifecycleService.canSettle(existing.get())) {
                Payment stored = existing.get();
                IdempotencyService.PacketState state = switch (stored.getStatus()) {
                    case SETTLED -> IdempotencyService.PacketState.SETTLED;
                    case REJECTED -> IdempotencyService.PacketState.REJECTED;
                    case EXPIRED -> IdempotencyService.PacketState.EXPIRED;
                    default -> null;
                };
                if (state != null) {
                    if (!claimed && redisAvailable) {
                        try { idempotency.repairTerminal(packetHash, claim, state); }
                        catch (Exception unavailable) { log.warn("Replay cache repair unavailable"); }
                        throw new DuplicatePaymentException();
                    }
                    finalized = true;
                    if (claimed) updateState(packetHash, attemptToken, state);
                    return new IngestResult(stored.getStatus().name(), packetHash,
                            stored.getFailureCode(), lifecycle.transactionId(stored));
                }
                throw new DuplicatePaymentException();
            }
            if (!redisAvailable) return IngestResult.failed(packetHash, FailureReason.TEMPORARY_PROCESSING_FAILURE);
            if (!claimed && claim.state() != IdempotencyService.PacketState.PROCESSING) {
                claim = idempotency.reclaimAfterDatabaseCheck(packetHash, claim);
                claimed = claim.claimed();
                attemptToken = claim.token();
            }
            if (!claimed) return IngestResult.duplicate(packetHash);

            payment = lifecycle.register(instruction, packetHash, bridgeNodeId, hopCount);
            signatures.recordVerified(payment.getId(), instruction, verification);
            topology.linkPayment(packet.getPacketId(), payment.getId());

            Transaction tx;
            try {
                tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            } catch (DomainException e) {
                throw e;
            } catch (Exception e) {
                throw new SettlementException("Settlement operation failed", e);
            }
            if (tx.getStatus() == Transaction.Status.SETTLED) {
                finalized = true;
                updateState(packetHash, attemptToken, IdempotencyService.PacketState.SETTLED);
                return IngestResult.settled(packetHash, tx);
            }
            throw new SettlementException("Unexpected transaction status");
        } catch (DomainException e) {
            log.error("Ingestion error packetHash={} exceptionType={}", packetHash, e.getClass().getSimpleName());
            if (payment != null && !(e instanceof DuplicatePaymentException)
                    && !(e instanceof InsufficientBalanceException) && !(e instanceof DeviceRevokedException)) {
                recordFailure(payment, e);
            }
            if (claimed) {
                if (e instanceof ExpiredPacketException) {
                    updateState(packetHash, attemptToken, IdempotencyService.PacketState.EXPIRED);
                } else if (e instanceof DuplicatePaymentException) {
                    // A later lease owner may race an earlier DB commit. Preserve its terminal result.
                    Payment.Status stored = lifecycle.statusByHash(packetHash);
                    updateState(packetHash, attemptToken, stored == Payment.Status.SETTLED
                            ? IdempotencyService.PacketState.SETTLED : stored == Payment.Status.REJECTED
                            ? IdempotencyService.PacketState.REJECTED : IdempotencyService.PacketState.FAILED_RETRYABLE);
                } else if (e instanceof InvalidPacketException || e instanceof InvalidSignatureException
                        || e instanceof InsufficientBalanceException || e instanceof DeviceRevokedException) {
                    updateState(packetHash, attemptToken, IdempotencyService.PacketState.REJECTED);
                } else if (!finalized) {
                    updateState(packetHash, attemptToken, IdempotencyService.PacketState.FAILED_RETRYABLE);
                }
            }
            throw e;
        } catch (Exception e) {
            log.error("Ingestion error packetHash={} exceptionType={}", packetHash, e.getClass().getSimpleName());
            if (payment != null && !finalized) recordFailure(payment, new SettlementException());
            if (claimed && !finalized) {
                updateState(packetHash, attemptToken, IdempotencyService.PacketState.FAILED_RETRYABLE);
            }
            return IngestResult.failed(packetHash, FailureReason.PAYMENT_PROCESSING_FAILED);
        }
    }

    private void recordFailure(Payment payment, DomainException failure) {
        try {
            lifecycle.recordFailure(payment.getId(), payment.getVersion(), failure);
        } catch (Exception e) {
            log.error("Payment failure persistence failed paymentId={} exceptionType={}", payment.getPaymentId(), e.getClass().getSimpleName());
        }
    }

    private void updateState(String hash, String token, IdempotencyService.PacketState state) {
        try {
            switch (state) {
                case SETTLED -> idempotency.markSettled(hash, token);
                case REJECTED -> idempotency.markRejected(hash, token);
                case EXPIRED -> idempotency.markExpired(hash, token);
                case FAILED_RETRYABLE -> idempotency.markFailedRetryable(hash, token);
                default -> throw new IllegalArgumentException("Unsupported state");
            }
        } catch (Exception e) {
            log.error("Packet state update failed packetHash={} state={} exceptionType={}", hash, state, e.getClass().getSimpleName());
        }
    }

    private String validatePacketEnvelope(MeshPacket packet, int hopCount) {
        if (packet == null) {
            return "packet_required";
        }
        Set<ConstraintViolation<MeshPacket>> violations = validator.validate(packet);
        if (!violations.isEmpty()) {
            return violations.iterator().next().getPropertyPath().toString() + "_invalid";
        }
        if (packet.getTtl() > maxTtl) {
            return "ttl_above_max";
        }
        if (hopCount < 0) {
            return "hop_count_negative";
        }
        if (hopCount > maxHopCount) {
            return "hop_count_above_max";
        }
        if (packet.getCiphertext().length() > maxPayloadSize) {
            return "ciphertext_too_large";
        }
        return null;
    }

    private String validateInstruction(PaymentInstruction instruction) {
        if (instruction == null) return "instruction_required";
        Set<ConstraintViolation<PaymentInstruction>> violations = validator.validate(instruction);
        if (!violations.isEmpty()) {
            return violations.iterator().next().getPropertyPath().toString() + "_invalid";
        }
        if (instruction.getSenderVpa().equals(instruction.getReceiverVpa())) {
            return "sender_receiver_same";
        }
        if (instruction.getSenderVpa().length() > 255 || instruction.getReceiverVpa().length() > 255) {
            return "account_vpa_too_long";
        }
        try {
            if (instruction.getAmount().setScale(2, java.math.RoundingMode.UNNECESSARY).precision() > 19) {
                return "amount_out_of_range";
            }
        } catch (ArithmeticException e) {
            return "amount_scale_invalid";
        }
        return null;
    }

    public enum FailureReason {
        PAYMENT_PROCESSING_FAILED, TEMPORARY_PROCESSING_FAILURE, SETTLEMENT_FAILED
    }

    public record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, Transaction tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }

        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE", hash, null, null);
        }

        public static IngestResult failed(String hash, FailureReason reason) {
            return new IngestResult("FAILED", hash, reason.name(), null);
        }
    }
}
