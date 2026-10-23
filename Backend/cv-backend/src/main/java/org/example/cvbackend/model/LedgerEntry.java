package org.example.cvbackend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Off-chain index of a confirmed blockchain transaction, so the UI can list history quickly. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false)
    private String detail;

    private Long assetId;

    @Column(nullable = false, length = 66)
    private String txHash;

    @Column(nullable = false)
    private long blockNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {}

    public LedgerEntry(String title, String detail, Long assetId, String txHash, long blockNumber) {
        this.title = title;
        this.detail = detail;
        this.assetId = assetId;
        this.txHash = txHash;
        this.blockNumber = blockNumber;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public Long getAssetId() { return assetId; }
    public String getTxHash() { return txHash; }
    public long getBlockNumber() { return blockNumber; }
    public Instant getCreatedAt() { return createdAt; }
}