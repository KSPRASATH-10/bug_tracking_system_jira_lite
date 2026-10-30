package com.enterprise.bts.web.controller;

import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.service.AttachmentService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.dto.AttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/bugs/{bugId}/attachments")
@Tag(name = "Attachments", description = "Validated file uploads (max 5 MB; images, PDF, plain text)")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "File rejected (type, size, extension or content mismatch)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Not a member of this project",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Bug or attachment not found in this project",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "413", description = "File larger than 5 MB",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "List attachments")
    public List<AttachmentResponse> list(@PathVariable UUID projectId, @PathVariable UUID bugId) {
        return attachmentService.list(projectId, bugId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Upload an attachment (multipart field name: file)",
            description = "Content is verified by magic bytes (Apache Tika) against the extension and declared "
                    + "MIME type. Dangerous extensions (.sh, .exe, .jsp, .html, ...) are rejected.")
    public AttachmentResponse upload(@PathVariable UUID projectId, @PathVariable UUID bugId,
                                     @RequestPart("file") MultipartFile file,
                                     @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        return attachmentService.upload(projectId, bugId, file, caller);
    }

    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Download an attachment",
            description = "Always served with Content-Disposition: attachment and X-Content-Type-Options: nosniff, "
                    + "so a browser never renders or executes the file inline.")
    public ResponseEntity<Resource> download(@PathVariable UUID projectId, @PathVariable UUID bugId,
                                             @PathVariable UUID attachmentId) {
        AttachmentService.AttachmentDownload file = attachmentService.download(projectId, bugId, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .body(file.resource());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")
    @Operation(summary = "Archive an attachment (soft delete; uploader, PROJECT_LEAD or ADMINISTRATOR)")
    public void archive(@PathVariable UUID projectId, @PathVariable UUID bugId, @PathVariable UUID attachmentId,
                        @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal caller) {
        attachmentService.archive(projectId, bugId, attachmentId, caller);
    }

    /**
     * Forces download. The quoted fallback keeps only [A-Za-z0-9._-]; the RFC 5987 form carries the real
     * (already sanitised) name percent-encoded, so no header-injection characters can survive either way.
     */
    static String contentDisposition(String filename) {
        String ascii = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
