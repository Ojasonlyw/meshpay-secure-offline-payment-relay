package com.demo.upimesh.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Permanent record of every settled transaction. Once written, never modified.
 * The packetHash is the idempotency key — uniqueness is enforced at the DB level
 * as a defense-in-depth fallback if the Redis-style cache layer ever fails.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(name = "packet_hash", nullable = false, unique = true, length = 64)
    private String packetHash; // SHA-256 hex of the encrypted packet

    @Column(name = "sender_vpa", nullable = false, length = 255)
    private String senderVpa;

    @Column(name = "receiver_vpa", nullable = false, length = 255)
    private String receiverVpa;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "signed_at", nullable = false, columnDefinition = "timestamptz")
    private Instant signedAt; // When the sender originally signed it (offline)

    @Column(name = "settled_at", nullable = false, columnDefinition = "timestamptz")
    private Instant settledAt; // When the backend actually processed it

    @Column(name = "bridge_node_id", nullable = false, length = 255)
    private String bridgeNodeId; // Which mesh node finally delivered it

    @Column(name = "hop_count", nullable = false)
    private int hopCount; // How many devices it passed through

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status;

    public enum Status { SETTLED, REJECTED, FAILED, EXPIRED }

    public Transaction() {}

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPacketHash() { return packetHash; }
    public void setPacketHash(String packetHash) { this.packetHash = packetHash; }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String senderVpa) { this.senderVpa = senderVpa; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String receiverVpa) { this.receiverVpa = receiverVpa; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public String getBridgeNodeId() { return bridgeNodeId; }
    public void setBridgeNodeId(String bridgeNodeId) { this.bridgeNodeId = bridgeNodeId; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
