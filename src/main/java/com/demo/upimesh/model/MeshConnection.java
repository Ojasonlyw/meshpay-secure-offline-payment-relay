package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mesh_connections")
public class MeshConnection {
    public enum Status { ACTIVE, INACTIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_device_id", nullable = false)
    private Device sourceDevice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_device_id", nullable = false)
    private Device targetDevice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Status status;
    @Column(name = "link_type", nullable = false, length = 32)
    private String linkType;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();
    @Column(name = "last_seen_at", columnDefinition = "timestamptz")
    private Instant lastSeenAt;

    public Long getId() { return id; }
    public Device getSourceDevice() { return sourceDevice; }
    public void setSourceDevice(Device value) { sourceDevice = value; }
    public Device getTargetDevice() { return targetDevice; }
    public void setTargetDevice(Device value) { targetDevice = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String value) { linkType = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { lastSeenAt = value; }
}
