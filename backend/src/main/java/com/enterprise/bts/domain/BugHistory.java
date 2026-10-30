package com.enterprise.bts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.Immutable;

/** Append-only change ledger for a bug (status transitions, assignments, edits, comments, attachments). */
@Entity
@Immutable
@Table(name = "bug_history")
public class BugHistory {

    private static final int MAX_VALUE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bug_id", nullable = false, updatable = false)
    private UUID bugId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private HistoryEventType eventType;

    @Column(name = "field_name", updatable = false, length = 60)
    private String fieldName;

    @Column(name = "old_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BugHistory() {
        // JPA
    }

    public BugHistory(UUID bugId, User actor, HistoryEventType eventType,
                      String fieldName, String oldValue, String newValue) {
        this.bugId = bugId;
        this.actor = actor;
        this.eventType = eventType;
        this.fieldName = fieldName;
        this.oldValue = clip(oldValue);
        this.newValue = clip(newValue);
    }

    private static String clip(String value) {
        if (value == null || value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH - 1) + "\u2026";
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBugId() {
        return bugId;
    }

    public User getActor() {
        return actor;
    }

    public HistoryEventType getEventType() {
        return eventType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
