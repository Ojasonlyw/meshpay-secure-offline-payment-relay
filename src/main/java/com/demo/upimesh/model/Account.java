package com.demo.upimesh.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Simulated bank account. In a real system this would live in the bank's core,
 * not in our service. For the demo, we own the ledger.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "vpa", nullable = false, length = 255)
    private String vpa; // Virtual Payment Address, e.g. "alice@demo"

    @Column(name = "holder_name", nullable = false, length = 255)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Version  // Optimistic locking — prevents lost updates on concurrent transfers
    @Column(name = "version")
    private Long version;

    public Account() {}

    public Account(String vpa, String holderName, BigDecimal balance) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
        this.openingBalance = balance;
    }

    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
