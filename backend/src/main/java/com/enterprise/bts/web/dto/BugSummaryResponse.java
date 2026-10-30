package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugPriority;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Resolution;
import java.time.Instant;
import java.util.UUID;

/** Compact row for list views and the dashboard (no description body). */
public record BugSummaryResponse(
        UUID id,
        String key,
        UUID projectId,
        int bugNumber,
        String title,
        BugStatus status,
        BugPriority priority,
        Resolution resolution,
        String reporterName,
        UUID assigneeId,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt) {

    public static BugSummaryResponse from(Bug bug) {
        return new BugSummaryResponse(
                bug.getId(),
                bug.getKey(),
                bug.getProject().getId(),
                bug.getBugNumber(),
                bug.getTitle(),
                bug.getStatus(),
                bug.getPriority(),
                bug.getResolution(),
                bug.getReporter().getFullName(),
                bug.getAssignee() == null ? null : bug.getAssignee().getId(),
                bug.getAssignee() == null ? null : bug.getAssignee().getFullName(),
                bug.getCreatedAt(),
                bug.getUpdatedAt());
    }
}
