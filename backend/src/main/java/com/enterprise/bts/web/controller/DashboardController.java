package com.enterprise.bts.web.controller;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.service.DashboardService;
import com.enterprise.bts.web.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Role-aware summary across the caller's accessible projects")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Dashboard",
            description = "Counts and the caller's open assignments, restricted to projects the caller can access.")
    public DashboardResponse dashboard(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return dashboardService.build(caller);
    }
}
