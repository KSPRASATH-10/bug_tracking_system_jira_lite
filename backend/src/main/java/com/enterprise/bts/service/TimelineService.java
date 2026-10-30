package com.enterprise.bts.service;

import com.enterprise.bts.domain.BugComment;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.HistoryEventType;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.repository.BugCommentRepository;
import com.enterprise.bts.repository.BugHistoryRepository;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.web.dto.TimelineEntryResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merges the immutable change ledger and the comments into one chronological activity feed. */
@Service
public class TimelineService {

    private final BugRepository bugRepository;
    private final BugHistoryRepository historyRepository;
    private final BugCommentRepository commentRepository;

    public TimelineService(BugRepository bugRepository, BugHistoryRepository historyRepository,
                           BugCommentRepository commentRepository) {
        this.bugRepository = bugRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> timeline(UUID projectId, UUID bugId) {
        bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));

        List<TimelineEntryResponse> entries = new ArrayList<>();
        for (BugHistory history : historyRepository.findByBugIdOrderByCreatedAtAsc(bugId)) {
            if (history.getEventType() == HistoryEventType.COMMENTED) {
                continue;               // shown once, as the COMMENT entry below (with its text)
            }
            entries.add(TimelineEntryResponse.fromHistory(history));
        }
        for (BugComment comment : commentRepository.findByBugIdOrderByCreatedAtAsc(bugId)) {
            entries.add(TimelineEntryResponse.fromComment(comment));
        }
        entries.sort(Comparator.comparing(TimelineEntryResponse::createdAt)
                .thenComparing(TimelineEntryResponse::id));
        return entries;
    }
}
