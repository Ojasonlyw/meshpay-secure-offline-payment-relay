package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_signatures")
public class PaymentSignature {
    public enum VerificationStatus { VERIFIED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;
    @Column(columnDefinition = "text")
    private String signature;
    @Column(name = "signature_algorithm", length = 32)
    private String signatureAlgorithm;
    @Column(name = "canonical_payload_hash", nullable = false, length = 64)
    private String canonicalPayloadHash;
    @Column(name = "verified_at", nullable = false, columnDefinition = "timestamptz")
    private Instant verifiedAt = Instant.now();
    @Enumerated(EnumType.STRING) @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus;
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment value) { payment = value; }
    public Device getDevice() { return device; }
    public void setDevice(Device value) { device = value; }
    public String getSignature() { return signature; }
    public void setSignature(String value) { signature = value; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String value) { signatureAlgorithm = value; }
    public String getCanonicalPayloadHash() { return canonicalPayloadHash; }
    public void setCanonicalPayloadHash(String value) { canonicalPayloadHash = value; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant value) { verifiedAt = value; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus value) { verificationStatus = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { failureCode = value; }
}
