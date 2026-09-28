package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {
    public enum Status { ACTIVE, INACTIVE, REVOKED }
    public enum TrustStatus { TRUSTED, UNTRUSTED, BLOCKED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "device_name", nullable = false)
    private String deviceName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Status status;
    @Column(name = "internet_capability", nullable = false)
    private boolean internetCapability;
    @Enumerated(EnumType.STRING) @Column(name = "trust_status", nullable = false, length = 32)
    private TrustStatus trustStatus;
    @Column(name = "registered_at", nullable = false, columnDefinition = "timestamptz")
    private Instant registeredAt = Instant.now();
    @Column(name = "last_seen_at", columnDefinition = "timestamptz")
    private Instant lastSeenAt;
    @Version @Column(nullable = false)
    private Long version;

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String value) { deviceName = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public boolean isInternetCapability() { return internetCapability; }
    public void setInternetCapability(boolean value) { internetCapability = value; }
    public TrustStatus getTrustStatus() { return trustStatus; }
    public void setTrustStatus(TrustStatus value) { trustStatus = value; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant value) { registeredAt = value; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { lastSeenAt = value; }
    public Long getVersion() { return version; }
}
