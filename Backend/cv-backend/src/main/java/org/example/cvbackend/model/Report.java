package org.example.cvbackend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reports")
public class Report {

    public enum Kind { LOST, FOUND }
    public enum Status { OPEN, MATCHED, RETURNED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 120)
    private String location;

    /** The day the item was lost or found. */
    @Column(nullable = false)
    private LocalDate eventDate;

    /** Stored file name inside the upload folder (never taken from the client). */
    @Column(nullable = false, length = 80)
    private String imageFile;

    /** SHA-256 of the photo bytes, hex. */
    @Column(nullable = false, length = 64)
    private String imageHash;

    /** Keccak-256 of the details plus the photo hash, also stored on-chain. */
    @Column(nullable = false, length = 66)
    private String dataHash;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @ManyToOne
    @JoinColumn(name = "matched_with_id")
    private Report matchedWith;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static String codeFor(Long id) { return "LF-" + (1000 + id); }

    public Long getId() { return id; }
    public String getCode() { return codeFor(id); }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getImageFile() { return imageFile; }
    public void setImageFile(String imageFile) { this.imageFile = imageFile; }
    public String getImageHash() { return imageHash; }
    public void setImageHash(String imageHash) { this.imageHash = imageHash; }
    public String getDataHash() { return dataHash; }
    public void setDataHash(String dataHash) { this.dataHash = dataHash; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Report getMatchedWith() { return matchedWith; }
    public void setMatchedWith(Report matchedWith) { this.matchedWith = matchedWith; }
    public Instant getCreatedAt() { return createdAt; }
    /** Image embedding from the matching service, comma-separated. Null until it has been computed. */
    @Column(columnDefinition = "text")
    private String embedding;

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
}