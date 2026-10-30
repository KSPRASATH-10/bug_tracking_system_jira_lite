package com.enterprise.bts.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Project key is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
                message = "Key must be 2-10 characters: uppercase letters/digits, starting with a letter")
        String projectKey,

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
