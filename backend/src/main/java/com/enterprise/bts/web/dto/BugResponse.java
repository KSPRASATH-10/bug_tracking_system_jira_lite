package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugPriority;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Resolution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full bug detail. {@code allowedTransitions} is already filtered by both the state machine and the
 * caller's project role, so the UI only renders transitions the server will accept.
 */
public record BugResponse(
        UUID id,
        String key,
        UUID projectId,
        String projectKey,
        int bugNumber,
        String title,
        String description,
        BugStatus status,
        BugPriority priority,
        Resolution resolution,
        UserResponse reporter,
        UserResponse assignee,
        Long version,
        List<BugStatus> allowedTransitions,
        Instant createdAt,
        Instant updatedAt) {

    public static BugResponse from(Bug bug, List<BugStatus> allowedTransitions) {
        return new BugResponse(
                bug.getId(),
                bug.getKey(),
                bug.getProject().getId(),
                bug.getProject().getProjectKey(),
                bug.getBugNumber(),
                bug.getTitle(),
                bug.getDescription(),
                bug.getStatus(),
                bug.getPriority(),
                bug.getResolution(),
                UserResponse.from(bug.getReporter()),
                bug.getAssignee() == null ? null : UserResponse.from(bug.getAssignee()),
                bug.getVersion(),
                List.copyOf(allowedTransitions),
                bug.getCreatedAt(),
                bug.getUpdatedAt());
    }
}
