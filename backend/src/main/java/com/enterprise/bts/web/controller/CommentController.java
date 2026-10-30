package com.enterprise.bts.web.controller;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.service.CommentService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.dto.CommentRequest;
import com.enterprise.bts.web.dto.CommentResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Append-only: there is intentionally no PUT/PATCH/DELETE for comments. */
@RestController
@RequestMapping("/api/projects/{projectId}/bugs/{bugId}/comments")
@Tag(name = "Comments", description = "Append-only discussion on a bug")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Not a member of this project",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Bug not found in this project",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "List comments (oldest first)")
    public List<CommentResponse> list(@PathVariable UUID projectId, @PathVariable UUID bugId) {
        return commentService.list(projectId, bugId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Add a comment (Markdown, sanitised server-side; any project member)")
    public CommentResponse add(@PathVariable UUID projectId, @PathVariable UUID bugId,
                               @Valid @RequestBody CommentRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return commentService.add(projectId, bugId, request, caller);
    }
}
