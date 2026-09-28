package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {
    public enum Direction { DEBIT, CREDIT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;
    @Column(name = "account_vpa", nullable = false, length = 255)
    private String accountVpa;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(Payment payment, Transaction transaction, String accountVpa,
                       Direction direction, BigDecimal amount, Instant createdAt) {
        this.payment = payment;
        this.transaction = transaction;
        this.accountVpa = accountVpa;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public Transaction getTransaction() { return transaction; }
    public String getAccountVpa() { return accountVpa; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
