package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    @Column(nullable = false, unique = true)
    private String vpa;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public String getVpa() { return vpa; }
    public void setVpa(String value) { vpa = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
