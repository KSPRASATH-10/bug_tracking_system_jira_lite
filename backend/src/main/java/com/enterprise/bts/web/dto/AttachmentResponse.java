package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.Attachment;
import java.time.Instant;
import java.util.UUID;

/** Deliberately omits the on-disk storage name: clients address files by attachment id only. */
public record AttachmentResponse(
        UUID id,
        UUID bugId,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        UserResponse uploader,
        Instant createdAt) {

    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getBugId(), a.getOriginalFilename(), a.getContentType(),
                a.getSizeBytes(), a.getSha256(), UserResponse.from(a.getUploader()), a.getCreatedAt());
    }
}
