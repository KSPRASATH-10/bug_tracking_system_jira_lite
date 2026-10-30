package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.Project;
import com.enterprise.bts.domain.ProjectRole;
import java.time.Instant;
import java.util.UUID;

/** {@code myRole} is null for an administrator who is not an explicit member of the project. */
public record ProjectResponse(
        UUID id,
        String projectKey,
        String name,
        String description,
        boolean archived,
        ProjectRole myRole,
        Instant createdAt) {

    public static ProjectResponse from(Project project, ProjectRole myRole) {
        return new ProjectResponse(project.getId(), project.getProjectKey(), project.getName(),
                project.getDescription(), project.isArchived(), myRole, project.getCreatedAt());
    }
}
