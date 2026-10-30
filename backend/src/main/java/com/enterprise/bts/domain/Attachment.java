package com.enterprise.bts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Attachment metadata. The bytes live on disk under {@code storageName} (an opaque UUID, never
 * derived from user input). Attachments are archived, never deleted.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bug_id", nullable = false, updatable = false)
    private UUID bugId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false, updatable = false)
    private User uploader;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_name", nullable = false, updatable = false, unique = true)
    private UUID storageName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
        // JPA
    }

    public Attachment(UUID bugId, User uploader, String originalFilename, UUID storageName,
                      String contentType, long sizeBytes, String sha256) {
        this.bugId = bugId;
        this.uploader = uploader;
        this.originalFilename = originalFilename;
        this.storageName = storageName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void archive() {
        this.archived = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBugId() {
        return bugId;
    }

    public User getUploader() {
        return uploader;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public UUID getStorageName() {
        return storageName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
