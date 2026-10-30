package com.enterprise.bts.service;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugComment;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.HistoryEventType;
import com.enterprise.bts.domain.User;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.repository.BugCommentRepository;
import com.enterprise.bts.repository.BugHistoryRepository;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.repository.UserRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.web.dto.CommentRequest;
import com.enterprise.bts.web.dto.CommentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Comments are append-only: there is deliberately no edit or delete operation. */
@Service
public class CommentService {

    private static final int MAX_COMMENT_LENGTH = 5000;

    private final BugRepository bugRepository;
    private final BugCommentRepository commentRepository;
    private final BugHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MarkdownSanitizer sanitizer;
    private final AuditService auditService;

    public CommentService(BugRepository bugRepository, BugCommentRepository commentRepository,
                          BugHistoryRepository historyRepository, UserRepository userRepository,
                          MarkdownSanitizer sanitizer, AuditService auditService) {
        this.bugRepository = bugRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.sanitizer = sanitizer;
        this.auditService = auditService;
    }

    @Transactional
    public CommentResponse add(UUID projectId, UUID bugId, CommentRequest request, AppUserPrincipal caller) {
        Bug bug = bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        if (bug.getProject().isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived and read-only");
        }
        String body = sanitizer.sanitizeMarkdown(request.body(), MAX_COMMENT_LENGTH, "Comment");
        if (body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment must not be empty");
        }
        User author = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AccessDeniedException("Account no longer exists"));

        BugComment comment = commentRepository.saveAndFlush(new BugComment(bug.getId(), author, body));
        historyRepository.save(new BugHistory(bug.getId(), author, HistoryEventType.COMMENTED, "comment",
                null, comment.getId().toString()));
        auditService.record("COMMENT_ADDED", "BUG", bug.getId(), Map.of("commentId", comment.getId().toString()));
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID projectId, UUID bugId) {
        bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
        return commentRepository.findByBugIdOrderByCreatedAtAsc(bugId).stream().map(CommentResponse::from).toList();
    }
}
