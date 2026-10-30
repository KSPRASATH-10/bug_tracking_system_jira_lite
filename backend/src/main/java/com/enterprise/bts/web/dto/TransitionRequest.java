package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Resolution;
import jakarta.validation.constraints.NotNull;

/** {@code resolution} is mandatory when {@code targetStatus} is CLOSED (enforced in the service and the DB). */
public record TransitionRequest(
        @NotNull(message = "Target status is required")
        BugStatus targetStatus,

        Resolution resolution,

        @NotNull(message = "Version is required for optimistic locking")
        Long version) {
}
