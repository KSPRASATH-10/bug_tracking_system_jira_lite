package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.BugComment;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.HistoryEventType;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable row of the activity timeline. {@code kind} is "HISTORY" (a recorded change) or
 * "COMMENT" (a user comment, whose text is in {@code body}).
 */
public record TimelineEntryResponse(
        String kind,
        UUID id,
        Instant createdAt,
        UserResponse actor,
        HistoryEventType eventType,
        String fieldName,
        String oldValue,
        String newValue,
        String body) {

    public static TimelineEntryResponse fromHistory(BugHistory h) {
        return new TimelineEntryResponse("HISTORY", h.getId(), h.getCreatedAt(),
                UserResponse.from(h.getActor()), h.getEventType(), h.getFieldName(),
                h.getOldValue(), h.getNewValue(), null);
    }

    public static TimelineEntryResponse fromComment(BugComment c) {
        return new TimelineEntryResponse("COMMENT", c.getId(), c.getCreatedAt(),
                UserResponse.from(c.getAuthor()), null, null, null, null, c.getBody());
    }
}
