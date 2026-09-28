package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_keys")
public class DeviceKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;
    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;
    @Column(nullable = false, length = 32)
    private String algorithm;
    @Column(name = "key_fingerprint", nullable = false, unique = true, length = 64)
    private String keyFingerprint;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();
    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private Instant revokedAt;

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device value) { device = value; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String value) { publicKey = value; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String value) { algorithm = value; }
    public String getKeyFingerprint() { return keyFingerprint; }
    public void setKeyFingerprint(String value) { keyFingerprint = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant value) { revokedAt = value; }
}
