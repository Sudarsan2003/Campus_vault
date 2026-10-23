package org.example.cvbackend.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "assets")
public class Asset {

    public enum Status { PENDING, AVAILABLE, BORROWED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(length = 500)
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Keccak-256 of the asset details, also stored on-chain. */
    @Column(nullable = false, length = 66)
    private String dataHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static String codeFor(Long id) { return "CV-" + (1000 + id); }

    public Long getId() { return id; }
    public String getCode() { return codeFor(id); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    public Instant getCreatedAt() { return createdAt; }
}