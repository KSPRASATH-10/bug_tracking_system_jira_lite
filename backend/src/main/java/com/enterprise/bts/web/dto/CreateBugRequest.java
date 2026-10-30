package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.BugPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBugRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 20000, message = "Description must be at most 20000 characters")
        String description,

        @NotNull(message = "Priority is required")
        BugPriority priority) {
}
