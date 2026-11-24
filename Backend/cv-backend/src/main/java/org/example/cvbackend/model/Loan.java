package org.example.cvbackend.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "loans")
public class Loan {

    public enum Status { OPEN, RETURNED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(optional = false)
    @JoinColumn(name = "borrower_id")
    private User borrower;

    @Column(nullable = false)
    private long deposit;

    @Column(nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant returnedAt;

    @Column(nullable = false)
    private boolean damaged;

    /** Set once the overdue monitor has recorded this loan as overdue on-chain. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean overdueFlagged;

    /** Tokens kept from the deposit for a late return (0 if on time or damaged). */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long lateFee;

    public Long getId() { return id; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public User getBorrower() { return borrower; }
    public void setBorrower(User borrower) { this.borrower = borrower; }
    public long getDeposit() { return deposit; }
    public void setDeposit(long deposit) { this.deposit = deposit; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public boolean isDamaged() { return damaged; }
    public void setDamaged(boolean damaged) { this.damaged = damaged; }
    public boolean isOverdueFlagged() { return overdueFlagged; }
    public void setOverdueFlagged(boolean overdueFlagged) { this.overdueFlagged = overdueFlagged; }
    public long getLateFee() { return lateFee; }
    public void setLateFee(long lateFee) { this.lateFee = lateFee; }

    /** Open and past its due date at the given (chain) time. */
    public boolean isOverdueAt(Instant now) { return status == Status.OPEN && now.isAfter(dueAt); }
}