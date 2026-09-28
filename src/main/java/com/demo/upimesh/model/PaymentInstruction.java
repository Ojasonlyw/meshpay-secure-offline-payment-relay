package com.demo.upimesh.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * The actual payment instruction. After the server decrypts MeshPacket.ciphertext,
 * it gets one of these.
 *
 * Critical fields for security:
 *   - nonce: a UUID unique to this payment. Even if everything else were identical
 *            for two legitimate payments (alice sends bob ₹100 twice), the nonces
 *            differ, so the resulting ciphertexts and their hashes also differ.
 *   - signedAt: lets the server reject stale packets ("freshness window"). Without
 *               this, an attacker who got the ciphertext could replay it weeks later.
 *   - signature: a registered sender-device key authorizes the canonical fields.
 */
public class PaymentInstruction {

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String paymentId;

    @NotBlank
    private String senderVpa;

    @NotBlank
    private String receiverVpa;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String nonce;     // UUID, unique per payment intent

    @NotNull
    private Long signedAt;    // epoch millis, when sender signed

    @NotBlank
    private String deviceId;

    private String signature;
    private String signatureAlgorithm;

    public PaymentInstruction() {}

    public PaymentInstruction(String paymentId, String senderVpa, String receiverVpa,
                              BigDecimal amount, String nonce, Long signedAt, String deviceId) {
        this.paymentId = paymentId;
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.nonce = nonce;
        this.signedAt = signedAt;
        this.deviceId = deviceId;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String senderVpa) { this.senderVpa = senderVpa; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String receiverVpa) { this.receiverVpa = receiverVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public Long getSignedAt() { return signedAt; }
    public void setSignedAt(Long signedAt) { this.signedAt = signedAt; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }
}
