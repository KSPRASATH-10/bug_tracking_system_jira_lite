package com.enterprise.bts.web.dto;

import java.util.List;
import java.util.Map;

/** Role-aware dashboard payload: counts are limited to projects the caller can access. */
public record DashboardResponse(
        long accessibleProjects,
        Map<String, Long> bugsByStatus,
        long openAssignedToMe,
        List<BugSummaryResponse> myOpenBugs) {
}
