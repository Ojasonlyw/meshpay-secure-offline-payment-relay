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

    public record Verification(boolean valid, String failureCode, Long deviceId, String keyFingerprint) {}

    /** Pure authenticity check: no payment identity or audit row is reserved here. */
    @Transactional(readOnly = true)
    public Verification verify(PaymentInstruction instruction) {
        Device device = devices.findByDeviceId(instruction.getDeviceId()).orElse(null);
        DeviceKey key = device == null ? null : keys.findByDevice_IdAndActiveTrue(device.getId()).orElse(null);
        String failure = failure(instruction, device, key);
        return new Verification(failure == null, failure, device == null ? null : device.getId(),
                key == null ? null : key.getKeyFingerprint());
    }

    /** The one-row evidence is written only for a verified canonical identity; retries preserve it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVerified(Long paymentId, PaymentInstruction instruction, Verification verification) {
        if (!verification.valid()) throw new IllegalArgumentException("Verified instruction required");
        Payment payment = payments.lockById(paymentId).orElseThrow();
        if (signatures.findByPayment_Id(paymentId).isPresent()) return;
        PaymentSignature record = new PaymentSignature();
        record.setPayment(payment);
        record.setDevice(devices.getReferenceById(verification.deviceId()));
        record.setSignature(instruction.getSignature());
        record.setSignatureAlgorithm(instruction.getSignatureAlgorithm());
        record.setKeyFingerprint(verification.keyFingerprint());
        record.setCanonicalPayloadHash(sha256Hex(canonicalPayload(instruction)));
        record.setVerifiedAt(Instant.now());
        record.setVerificationStatus(PaymentSignature.VerificationStatus.VERIFIED);
        signatures.saveAndFlush(record);
    }

    private String failure(PaymentInstruction instruction, Device device, DeviceKey key) {
        if (instruction.getSignature() == null || instruction.getSignature().isBlank()) return "MISSING_SIGNATURE";
        if (!ALGORITHM.equals(instruction.getSignatureAlgorithm())) return "UNSUPPORTED_ALGORITHM";
        if (device == null) return "UNKNOWN_DEVICE";
        if (device.getStatus() == Device.Status.REVOKED) return "REVOKED_DEVICE";
        if (device.getStatus() != Device.Status.ACTIVE) return "INACTIVE_DEVICE";
        if (device.getTrustStatus() != Device.TrustStatus.TRUSTED) return "UNTRUSTED_DEVICE";
        if (!device.getUser().getVpa().equals(instruction.getSenderVpa())
                || !"ACTIVE".equals(device.getUser().getStatus())) return "WRONG_DEVICE_OWNER";
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
