package com.demo.upimesh.service;

import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.DeviceKey;
import com.demo.upimesh.model.Payment;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.PaymentSignature;
import com.demo.upimesh.repository.DeviceKeyRepository;
import com.demo.upimesh.repository.DeviceRepository;
import com.demo.upimesh.repository.PaymentRepository;
import com.demo.upimesh.repository.PaymentSignatureRepository;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceSignatureService {
    public static final String ALGORITHM = "Ed25519";
    private final DeviceRepository devices;
    private final DeviceKeyRepository keys;
    private final PaymentRepository payments;
    private final PaymentSignatureRepository signatures;

    public DeviceSignatureService(DeviceRepository devices, DeviceKeyRepository keys,
            PaymentRepository payments, PaymentSignatureRepository signatures) {
        this.devices = devices;
        this.keys = keys;
        this.payments = payments;
        this.signatures = signatures;
    }

    /** Version and length framing prevent ambiguous concatenations. */
    public byte[] canonicalPayload(PaymentInstruction instruction) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (String field : new String[]{"UPI-MESH-PAYMENT-V1", instruction.getPaymentId(),
                    instruction.getSenderVpa(), instruction.getReceiverVpa(),
                    instruction.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    instruction.getNonce(), Long.toString(instruction.getSignedAt()),
                    instruction.getDeviceId()}) {
                byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                out.writeInt(encoded.length);
                out.write(encoded);
            }
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid signing fields", e);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Verification(boolean valid, String failureCode) {}

    /** Returning a failure lets the audit transaction commit before ingestion rejects it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Verification verifyAndRecord(Long paymentId, PaymentInstruction instruction) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        Device device = devices.findByDeviceId(instruction.getDeviceId()).orElse(null);
        String failure = verify(instruction, device);
        PaymentSignature record = signatures.findByPayment_Id(paymentId).orElseGet(PaymentSignature::new);
        record.setPayment(payment);
        record.setDevice(device);
        record.setSignature(instruction.getSignature());
        record.setSignatureAlgorithm(instruction.getSignatureAlgorithm());
        record.setCanonicalPayloadHash(sha256Hex(canonicalPayload(instruction)));
        record.setVerifiedAt(Instant.now());
        record.setVerificationStatus(failure == null
                ? PaymentSignature.VerificationStatus.VERIFIED : PaymentSignature.VerificationStatus.FAILED);
        record.setFailureCode(failure);
        signatures.saveAndFlush(record);
        return new Verification(failure == null, failure);
    }

    private String verify(PaymentInstruction instruction, Device device) {
        if (instruction.getSignature() == null || instruction.getSignature().isBlank()) return "MISSING_SIGNATURE";
        if (!ALGORITHM.equals(instruction.getSignatureAlgorithm())) return "UNSUPPORTED_ALGORITHM";
        if (device == null) return "UNKNOWN_DEVICE";
        if (device.getStatus() == Device.Status.REVOKED) return "REVOKED_DEVICE";
        if (device.getStatus() != Device.Status.ACTIVE) return "INACTIVE_DEVICE";
        if (device.getTrustStatus() != Device.TrustStatus.TRUSTED) return "UNTRUSTED_DEVICE";
        if (!device.getUser().getVpa().equals(instruction.getSenderVpa())
                || !"ACTIVE".equals(device.getUser().getStatus())) return "WRONG_DEVICE_OWNER";
        DeviceKey key = keys.findByDevice_IdAndActiveTrue(device.getId()).orElse(null);
        if (key == null || key.getRevokedAt() != null) return "NO_ACTIVE_KEY";
        if (!ALGORITHM.equals(key.getAlgorithm())) return "UNSUPPORTED_ALGORITHM";
        try {
            byte[] raw = Base64.getDecoder().decode(instruction.getSignature());
            if (raw.length != 64) return "INVALID_SIGNATURE";
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(KeyFactory.getInstance(ALGORITHM).generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(key.getPublicKey()))));
            verifier.update(canonicalPayload(instruction));
            return verifier.verify(raw) ? null : "INVALID_SIGNATURE";
        } catch (Exception e) {
            return "INVALID_SIGNATURE";
        }
    }
}
