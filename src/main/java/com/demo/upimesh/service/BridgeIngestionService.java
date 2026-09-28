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
        boolean finalized = false;
        Payment payment = null;
        try {
            String packetError = validatePacketEnvelope(packet, hopCount);
            if (packetError != null) {
                throw new InvalidPacketException(packetError);
            }

            packetHash = crypto.hashCiphertext(packet.getCiphertext());

            IdempotencyService.ClaimResult claim = idempotency.claimProcessing(packetHash);
            if (!claim.claimed()) {
                if (claim.state() == IdempotencyService.PacketState.SETTLED
                        || claim.state() == IdempotencyService.PacketState.REJECTED
                        || claim.state() == IdempotencyService.PacketState.EXPIRED) {
                    throw new DuplicatePaymentException();
                }
                log.info("DUPLICATE packet {} from bridge {} dropped",
                        packetHash.substring(0, 12) + "...", bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }
            claimed = true;

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

            payment = lifecycle.register(instruction, packetHash, bridgeNodeId, hopCount);
            topology.linkPayment(packet.getPacketId(), payment.getId());

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
                    signatures.verifyAndRecord(payment.getId(), instruction);
            if (!verification.valid()) throw new InvalidSignatureException(verification.failureCode());

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
                updateState(packetHash, IdempotencyService.PacketState.SETTLED);
                return IngestResult.settled(packetHash, tx);
            }
            throw new SettlementException("Unexpected transaction status");
        } catch (DomainException e) {
            log.error("Ingestion error for packetHash={}", packetHash, e);
            if (payment != null && !(e instanceof DuplicatePaymentException)
                    && !(e instanceof InsufficientBalanceException)) {
                recordFailure(payment, e);
            }
            if (claimed) {
                if (e instanceof ExpiredPacketException) {
                    updateState(packetHash, IdempotencyService.PacketState.EXPIRED);
                } else if (e instanceof InvalidPacketException || e instanceof InvalidSignatureException
                        || e instanceof DuplicatePaymentException
                        || e instanceof InsufficientBalanceException) {
                    updateState(packetHash, IdempotencyService.PacketState.REJECTED);
                } else if (!finalized) {
                    updateState(packetHash, IdempotencyService.PacketState.FAILED_RETRYABLE);
                }
            }
            throw e;
        } catch (Exception e) {
            log.error("Ingestion error for packetHash={}", packetHash, e);
            if (payment != null && !finalized) recordFailure(payment, new SettlementException());
            if (claimed && !finalized) {
                updateState(packetHash, IdempotencyService.PacketState.FAILED_RETRYABLE);
            }
            return IngestResult.failed(packetHash, FailureReason.PAYMENT_PROCESSING_FAILED);
        }
    }

    private void recordFailure(Payment payment, DomainException failure) {
        try {
            lifecycle.recordFailure(payment.getId(), payment.getVersion(), failure);
        } catch (Exception e) {
            log.error("Payment failure persistence failed for paymentId={}", payment.getPaymentId(), e);
        }
    }

    private void updateState(String hash, IdempotencyService.PacketState state) {
        try {
            switch (state) {
                case SETTLED -> idempotency.markSettled(hash);
                case REJECTED -> idempotency.markRejected(hash);
                case EXPIRED -> idempotency.markExpired(hash);
                case FAILED_RETRYABLE -> idempotency.markFailedRetryable(hash);
                default -> throw new IllegalArgumentException("Unsupported state");
            }
        } catch (Exception e) {
            log.error("Packet state update failed for packetHash={} state={}", hash, state, e);
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
