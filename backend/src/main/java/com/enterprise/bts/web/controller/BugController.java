package com.enterprise.bts.web.controller;

import com.enterprise.bts.domain.BugPriority;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.service.BugService;
import com.enterprise.bts.service.TimelineService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.dto.AssignRequest;
import com.enterprise.bts.web.dto.BugResponse;
import com.enterprise.bts.web.dto.BugSummaryResponse;
import com.enterprise.bts.web.dto.CreateBugRequest;
import com.enterprise.bts.web.dto.PageResponse;
import com.enterprise.bts.web.dto.TimelineEntryResponse;
import com.enterprise.bts.web.dto.TransitionRequest;
import com.enterprise.bts.web.dto.UpdateBugRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every route is nested under /projects/{projectId} so the authorisation gate always has the project id,
 * and BugService resolves the bug only inside that project (a foreign bug id yields 404).
 */
@RestController
@RequestMapping("/api/projects/{projectId}/bugs")
@Tag(name = "Bugs", description = "Bug lifecycle: list, create, edit, transition, assign, archive, timeline")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation failed or illegal state transition",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Not a member of this project / insufficient project role",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Project or bug not found in this project",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Stale version (optimistic locking) or archived project",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class BugController {

    private final BugService bugService;
    private final TimelineService timelineService;

    public BugController(BugService bugService, TimelineService timelineService) {
        this.bugService = bugService;
        this.timelineService = timelineService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "List bugs (server-side pagination, sorting, filtering, search)",
            description = "sort is allow-listed: createdAt, updatedAt, title, status, priority, bugNumber. "
                    + "direction is asc or desc. size is capped at 100.")
    public PageResponse<BugSummaryResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) BugStatus status,
            @RequestParam(required = false) BugPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return bugService.list(projectId,
                new BugService.BugFilter(status, priority, assigneeId, search, page, size, sort, direction));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Report a bug (any project member)")
    public BugResponse create(@PathVariable UUID projectId,
                              @Valid @RequestBody CreateBugRequest request,
                              @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return bugService.create(projectId, request, caller);
    }

    @GetMapping("/{bugId}")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Get a bug",
            description = "allowedTransitions lists only the states the caller may legally move the bug to.")
    public BugResponse get(@PathVariable UUID projectId, @PathVariable UUID bugId,
                           @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return bugService.get(projectId, bugId, caller);
    }

    @PatchMapping("/{bugId}")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Edit title, description or priority",
            description = "DEVELOPER, PROJECT_LEAD and ADMINISTRATOR may edit any bug; a REPORTER only their own. "
                    + "Requires the current version (409 if stale).")
    public BugResponse update(@PathVariable UUID projectId, @PathVariable UUID bugId,
                              @Valid @RequestBody UpdateBugRequest request,
                              @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return bugService.update(projectId, bugId, request, caller);
    }

    @PostMapping("/{bugId}/transition")
    @PreAuthorize("@projectSecurity.isDeveloperOrLead(#projectId, authentication)")
    @Operation(summary = "Change status",
            description = "OPEN→IN_PROGRESS→RESOLVED→CLOSED|REOPENED, CLOSED→REOPENED, REOPENED→IN_PROGRESS. "
                    + "CLOSED requires a resolution; reopening clears it. Illegal transitions return 400.")
    public BugResponse transition(@PathVariable UUID projectId, @PathVariable UUID bugId,
                                  @Valid @RequestBody TransitionRequest request,
                                  @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return bugService.transition(projectId, bugId, request, caller);
    }

    @PutMapping("/{bugId}/assignee")
    @PreAuthorize("@projectSecurity.isLead(#projectId, authentication)")
    @Operation(summary = "Assign or unassign (PROJECT_LEAD or ADMINISTRATOR)",
            description = "The assignee must be a developer or lead of the same project. A null assigneeId unassigns.")
    public BugResponse assign(@PathVariable UUID projectId, @PathVariable UUID bugId,
                              @Valid @RequestBody AssignRequest request,
                              @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return bugService.assign(projectId, bugId, request, caller);
    }

    @DeleteMapping("/{bugId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.isLead(#projectId, authentication)")
    @Operation(summary = "Archive a bug (soft delete; PROJECT_LEAD or ADMINISTRATOR)")
    public void archive(@PathVariable UUID projectId, @PathVariable UUID bugId,
                        @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        bugService.archive(projectId, bugId, caller);
    }

    @GetMapping("/{bugId}/timeline")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Chronological, immutable activity timeline (history changes + comments)")
    public List<TimelineEntryResponse> timeline(@PathVariable UUID projectId, @PathVariable UUID bugId) {
        return timelineService.timeline(projectId, bugId);
    }
}
