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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bugs")
public class Bug {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** Read-only mirror of the FK so the project id is available without touching the lazy proxy. */
    @Column(name = "project_id", insertable = false, updatable = false)
    private UUID projectId;

    @Column(name = "bug_number", nullable = false, updatable = false)
    private int bugNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BugStatus status = BugStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private BugPriority priority = BugPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 30)
    private Resolution resolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Optimistic lock. A stale write surfaces as ObjectOptimisticLockingFailureException, mapped to HTTP 409. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bug() {
        // JPA
    }

    public Bug(Project project, int bugNumber, String title, String description,
               BugPriority priority, User reporter) {
        this.project = project;
        this.bugNumber = bugNumber;
        this.title = title;
        this.description = description == null ? "" : description;
        this.priority = priority;
        this.reporter = reporter;
        this.status = BugStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Applies an already-validated workflow transition while keeping the aggregate invariant:
     * CLOSED carries a Resolution; every other status carries none (reopening clears it).
     * Legality of the transition itself is decided by BugStateMachine in the service layer.
     */
    public void applyTransition(BugStatus next, Resolution closingResolution) {
        if (next == BugStatus.CLOSED && closingResolution == null) {
            throw new IllegalArgumentException("A resolution is required to close a bug");
        }
        this.status = next;
        this.resolution = (next == BugStatus.CLOSED) ? closingResolution : null;
    }

    public void archive() {
        this.archived = true;
        this.archivedAt = Instant.now();
    }

    public String getKey() {
        return project.getProjectKey() + "-" + bugNumber;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public UUID getProjectId() {
        return projectId != null ? projectId : project.getId();
    }

    public int getBugNumber() {
        return bugNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BugStatus getStatus() {
        return status;
    }

    public BugPriority getPriority() {
        return priority;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public User getReporter() {
        return reporter;
    }

    public User getAssignee() {
        return assignee;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void setPriority(BugPriority priority) {
        this.priority = priority;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }
}
