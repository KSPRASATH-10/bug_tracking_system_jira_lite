package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.BugComment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID bugId, UserResponse author, String body, Instant createdAt) {

    public static CommentResponse from(BugComment comment) {
        return new CommentResponse(comment.getId(), comment.getBugId(),
                UserResponse.from(comment.getAuthor()), comment.getBody(), comment.getCreatedAt());
    }
}
