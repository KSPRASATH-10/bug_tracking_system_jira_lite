package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.BugPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged. {@code version} is mandatory so a stale client edit
 * is rejected with HTTP 409 instead of silently overwriting a concurrent change.
 */
public record UpdateBugRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        @Pattern(regexp = "(?s).*\\S.*", message = "Title must not be blank")
        String title,

        @Size(max = 20000, message = "Description must be at most 20000 characters")
        String description,

        BugPriority priority,

        @NotNull(message = "Version is required for optimistic locking")
        Long version) {
}
