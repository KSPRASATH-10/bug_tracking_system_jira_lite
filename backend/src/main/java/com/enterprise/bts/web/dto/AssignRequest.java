package com.enterprise.bts.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** A null {@code assigneeId} unassigns the bug. */
public record AssignRequest(
        UUID assigneeId,

        @NotNull(message = "Version is required for optimistic locking")
        Long version) {
}
