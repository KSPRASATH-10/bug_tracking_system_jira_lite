package com.enterprise.bts.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Comment must not be empty")
        @Size(max = 5000, message = "Comment must be at most 5000 characters")
        String body) {
}
