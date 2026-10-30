package com.enterprise.bts.web.controller;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.service.ProjectService;
import com.enterprise.bts.web.dto.AddMemberRequest;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.dto.CreateProjectRequest;
import com.enterprise.bts.web.dto.MemberResponse;
import com.enterprise.bts.web.dto.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Projects and project-scoped membership (RBAC)")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Not a member of this project / insufficient project role",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Conflict (duplicate key, archived project, last lead)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Operation(summary = "List projects visible to the caller",
            description = "Administrators see all active projects; everyone else only projects they belong to.")
    public List<ProjectResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return projectService.listVisible(caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.isAdministrator(authentication)")
    @Operation(summary = "Create a project (ADMINISTRATOR only)")
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request,
                                  @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return projectService.create(request, caller);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Get a project")
    public ProjectResponse get(@PathVariable UUID projectId,
                               @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return projectService.get(projectId, caller);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.isLead(#projectId, authentication)")
    @Operation(summary = "Archive a project (soft delete; PROJECT_LEAD or ADMINISTRATOR)")
    public void archive(@PathVariable UUID projectId,
                        @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        projectService.archive(projectId, caller);
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "List project members", description = "Used by the UI to populate the assignee dropdown.")
    public List<MemberResponse> members(@PathVariable UUID projectId) {
        return projectService.listMembers(projectId);
    }

    @PutMapping("/{projectId}/members")
    @PreAuthorize("@projectSecurity.isLead(#projectId, authentication)")
    @Operation(summary = "Add a member or change their role (PROJECT_LEAD or ADMINISTRATOR)")
    public MemberResponse upsertMember(@PathVariable UUID projectId,
                                       @Valid @RequestBody AddMemberRequest request,
                                       @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return projectService.upsertMember(projectId, request, caller);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.isLead(#projectId, authentication)")
    @Operation(summary = "Remove a member (PROJECT_LEAD or ADMINISTRATOR)",
            description = "The member's open bugs are unassigned. A project always keeps at least one lead.")
    public void removeMember(@PathVariable UUID projectId, @PathVariable UUID userId,
                             @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        projectService.removeMember(projectId, userId, caller);
    }
}
