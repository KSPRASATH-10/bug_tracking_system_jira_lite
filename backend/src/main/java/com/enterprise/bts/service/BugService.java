package com.enterprise.bts.service;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.BugPriority;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.HistoryEventType;
import com.enterprise.bts.domain.Project;
import com.enterprise.bts.domain.ProjectRole;
import com.enterprise.bts.domain.Resolution;
import com.enterprise.bts.domain.User;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.repository.BugHistoryRepository;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.repository.BugSpecifications;
import com.enterprise.bts.repository.ProjectRepository;
import com.enterprise.bts.repository.UserRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.security.ProjectSecurity;
import com.enterprise.bts.web.dto.AssignRequest;
import com.enterprise.bts.web.dto.BugResponse;
import com.enterprise.bts.web.dto.BugSummaryResponse;
import com.enterprise.bts.web.dto.CreateBugRequest;
import com.enterprise.bts.web.dto.PageResponse;
import com.enterprise.bts.web.dto.TransitionRequest;
import com.enterprise.bts.web.dto.UpdateBugRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bug lifecycle. Security properties enforced here (in addition to the controller-level @PreAuthorize):
 *  - every lookup is scoped to the project the caller was authorised for (BOLA/IDOR),
 *  - role rules (who may edit / transition / assign) are re-checked,
 *  - stale writes are rejected with 409 via the client-supplied version AND Hibernate's @Version,
 *  - every change writes an append-only BugHistory row plus an audit row in the same transaction.
 */
@Service
public class BugService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;
    /** Allow-list of sortable properties: nothing user-supplied ever reaches ORDER BY unchecked. */
    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "title", "status", "priority", "bugNumber");

    /** List query parameters (all optional). {@code direction} is "asc" or "desc" (default desc). */
    public record BugFilter(BugStatus status, BugPriority priority, UUID assigneeId, String search,
                            Integer page, Integer size, String sort, String direction) {
    }

    private final BugRepository bugRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final BugHistoryRepository historyRepository;
    private final BugStateMachine stateMachine;
    private final MarkdownSanitizer sanitizer;
    private final ProjectSecurity projectSecurity;
    private final AuditService auditService;

    public BugService(BugRepository bugRepository, ProjectRepository projectRepository,
                      UserRepository userRepository, BugHistoryRepository historyRepository,
                      BugStateMachine stateMachine, MarkdownSanitizer sanitizer,
                      ProjectSecurity projectSecurity, AuditService auditService) {
        this.bugRepository = bugRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.stateMachine = stateMachine;
        this.sanitizer = sanitizer;
        this.projectSecurity = projectSecurity;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<BugSummaryResponse> list(UUID projectId, BugFilter filter) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found");
        }
        int page = filter.page() == null ? 0 : Math.max(0, filter.page());
        int size = filter.size() == null ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, filter.size()));
        String sortField = (filter.sort() == null || filter.sort().isBlank()) ? "createdAt" : filter.sort();
        if (!SORTABLE_FIELDS.contains(sortField)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        }
        Sort.Direction direction = parseDirection(filter.direction());
        String search = filter.search() == null ? null : filter.search().strip();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            search = search.substring(0, MAX_SEARCH_LENGTH);
        }

        Specification<Bug> spec = Specification.where(BugSpecifications.inProject(projectId))
                .and(BugSpecifications.notArchived())
                .and(BugSpecifications.hasStatus(filter.status()))
                .and(BugSpecifications.hasPriority(filter.priority()))
                .and(BugSpecifications.assignedTo(filter.assigneeId()))
                .and(BugSpecifications.matchesText(search));

        Pageable pageable;
        if ("priority".equals(sortField)) {
            // Severity order (BLOCKER..LOW), not alphabetical. "desc" = most severe first.
            spec = spec.and(orderByPriority(direction == Sort.Direction.DESC));
            pageable = PageRequest.of(page, size);
        } else {
            Sort sort = Sort.by(new Sort.Order(direction, sortField));
            if (!"bugNumber".equals(sortField)) {
                sort = sort.and(Sort.by(Sort.Direction.DESC, "bugNumber"));   // stable paging tie-breaker
            }
            pageable = PageRequest.of(page, size, sort);
        }
        Page<BugSummaryResponse> result = bugRepository.findAll(spec, pageable).map(BugSummaryResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public BugResponse get(UUID projectId, UUID bugId, AppUserPrincipal caller) {
        return toResponse(loadActive(projectId, bugId), caller);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public BugResponse create(UUID projectId, CreateBugRequest request, AppUserPrincipal caller) {
        // Row lock on the project serialises concurrent creations so bug numbers never collide.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        requireActive(project);
        String title = sanitizer.sanitizePlainText(request.title(), 200, "Title");
        String description = sanitizer.sanitizeMarkdown(request.description(), 20000, "Description");
        User reporter = actor(caller);

        Bug bug = new Bug(project, project.allocateBugNumber(), title, description, request.priority(), reporter);
        bugRepository.saveAndFlush(bug);
        history(bug, reporter, HistoryEventType.CREATED, "title", null, title);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("key", bug.getKey());
        details.put("priority", bug.getPriority().name());
        auditService.record("BUG_CREATED", "BUG", bug.getId(), details);
        return toResponse(bug, caller);
    }

    @Transactional
    public BugResponse update(UUID projectId, UUID bugId, UpdateBugRequest request, AppUserPrincipal caller) {
        Bug bug = loadActive(projectId, bugId);
        requireActive(bug.getProject());
        ProjectRole role = roleOf(projectId, caller);
        boolean ownReport = role == ProjectRole.REPORTER && bug.getReporter().getId().equals(caller.getId());
        if (!isDeveloperOrLead(caller, role) && !ownReport) {
            throw new AccessDeniedException("You may only edit bugs you reported");
        }
        assertVersion(bug, request.version());

        User actor = actor(caller);
        boolean changed = false;
        Map<String, Object> details = new LinkedHashMap<>();

        if (request.title() != null) {
            String title = sanitizer.sanitizePlainText(request.title(), 200, "Title");
            if (!title.equals(bug.getTitle())) {
                history(bug, actor, HistoryEventType.UPDATED, "title", bug.getTitle(), title);
                bug.setTitle(title);
                details.put("title", "changed");
                changed = true;
            }
        }
        if (request.description() != null) {
            String description = sanitizer.sanitizeMarkdown(request.description(), 20000, "Description");
            if (!description.equals(bug.getDescription())) {
                history(bug, actor, HistoryEventType.UPDATED, "description", bug.getDescription(), description);
                bug.setDescription(description);
                details.put("description", "changed");
                changed = true;
            }
        }
        if (request.priority() != null && request.priority() != bug.getPriority()) {
            history(bug, actor, HistoryEventType.UPDATED, "priority", bug.getPriority().name(),
                    request.priority().name());
            bug.setPriority(request.priority());
            details.put("priority", request.priority().name());
            changed = true;
        }
        if (changed) {
            bugRepository.saveAndFlush(bug);      // flush so the new @Version / updatedAt are reflected in the response
            auditService.record("BUG_UPDATED", "BUG", bug.getId(), details);
        }
        return toResponse(bug, caller);
    }

    @Transactional
    public BugResponse transition(UUID projectId, UUID bugId, TransitionRequest request, AppUserPrincipal caller) {
        Bug bug = loadActive(projectId, bugId);
        requireActive(bug.getProject());
        if (!isDeveloperOrLead(caller, roleOf(projectId, caller))) {
            throw new AccessDeniedException("Your project role cannot change bug status");
        }
        assertVersion(bug, request.version());

        BugStatus from = bug.getStatus();
        Resolution previousResolution = bug.getResolution();
        stateMachine.validate(from, request.targetStatus(), request.resolution());   // 400 on illegal transition

        bug.applyTransition(request.targetStatus(), request.resolution());
        User actor = actor(caller);
        history(bug, actor, HistoryEventType.STATUS_CHANGED, "status", from.name(), request.targetStatus().name());
        if (request.targetStatus() == BugStatus.CLOSED) {
            history(bug, actor, HistoryEventType.UPDATED, "resolution", null, request.resolution().name());
        } else if (previousResolution != null) {
            history(bug, actor, HistoryEventType.UPDATED, "resolution", previousResolution.name(), null);
        }
        bugRepository.saveAndFlush(bug);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.name());
        details.put("to", request.targetStatus().name());
        if (request.resolution() != null) {
            details.put("resolution", request.resolution().name());
        }
        auditService.record("BUG_STATUS_CHANGED", "BUG", bug.getId(), details);
        return toResponse(bug, caller);
    }

    @Transactional
    public BugResponse assign(UUID projectId, UUID bugId, AssignRequest request, AppUserPrincipal caller) {
        Bug bug = loadActive(projectId, bugId);
        requireActive(bug.getProject());
        if (!isLead(caller, roleOf(projectId, caller))) {
            throw new AccessDeniedException("Only project leads can assign bugs");
        }
        assertVersion(bug, request.version());

        User previous = bug.getAssignee();
        User actor = actor(caller);
        Map<String, Object> details = new LinkedHashMap<>();

        if (request.assigneeId() == null) {
            if (previous == null) {
                return toResponse(bug, caller);
            }
            bug.setAssignee(null);
            history(bug, actor, HistoryEventType.UNASSIGNED, "assignee", previous.getFullName(), null);
            details.put("assignee", "none");
        } else {
            // BOLA guard for assignment: the target must be a developer/lead of THIS project.
            ProjectRole targetRole = projectSecurity.roleOf(projectId, request.assigneeId()).orElse(null);
            if (targetRole != ProjectRole.DEVELOPER && targetRole != ProjectRole.PROJECT_LEAD) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assignee must be a developer or project lead of this project");
            }
            User target = userRepository.findById(request.assigneeId())
                    .filter(User::isEnabled)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Assignee must be a developer or project lead of this project"));
            if (previous != null && previous.getId().equals(target.getId())) {
                return toResponse(bug, caller);
            }
            bug.setAssignee(target);
            history(bug, actor, HistoryEventType.ASSIGNED, "assignee",
                    previous == null ? null : previous.getFullName(), target.getFullName());
            details.put("assignee", target.getId().toString());
        }
        bugRepository.saveAndFlush(bug);
        auditService.record(request.assigneeId() == null ? "BUG_UNASSIGNED" : "BUG_ASSIGNED", "BUG", bug.getId(), details);
        return toResponse(bug, caller);
    }

    /** Soft delete: the bug disappears from every view but all rows (history, comments, audit) remain. */
    @Transactional
    public void archive(UUID projectId, UUID bugId, AppUserPrincipal caller) {
        Bug bug = loadActive(projectId, bugId);
        requireActive(bug.getProject());
        if (!isLead(caller, roleOf(projectId, caller))) {
            throw new AccessDeniedException("Only project leads can archive bugs");
        }
        bug.archive();
        history(bug, actor(caller), HistoryEventType.ARCHIVED, "archived", null, "true");
        bugRepository.saveAndFlush(bug);
        auditService.record("BUG_ARCHIVED", "BUG", bug.getId(), Map.of("key", bug.getKey()));
    }

    // ------------------------------------------------------------------ helpers

    private Bug loadActive(UUID projectId, UUID bugId) {
        return bugRepository.findActiveInProject(bugId, projectId)
                .orElseThrow(() -> new NotFoundException("Bug not found"));
    }

    private BugResponse toResponse(Bug bug, AppUserPrincipal caller) {
        UUID projectId = bug.getProject().getId();
        boolean mayTransition = !bug.getProject().isArchived()
                && isDeveloperOrLead(caller, roleOf(projectId, caller));
        List<BugStatus> allowed = mayTransition ? stateMachine.allowedFrom(bug.getStatus()) : List.of();
        return BugResponse.from(bug, allowed);
    }

    private ProjectRole roleOf(UUID projectId, AppUserPrincipal caller) {
        return projectSecurity.roleOf(projectId, caller.getId()).orElse(null);
    }

    private static boolean isDeveloperOrLead(AppUserPrincipal caller, ProjectRole role) {
        return caller.isAdministrator() || role == ProjectRole.DEVELOPER || role == ProjectRole.PROJECT_LEAD;
    }

    private static boolean isLead(AppUserPrincipal caller, ProjectRole role) {
        return caller.isAdministrator() || role == ProjectRole.PROJECT_LEAD;
    }

    private User actor(AppUserPrincipal caller) {
        return userRepository.findById(caller.getId())
                .orElseThrow(() -> new AccessDeniedException("Account no longer exists"));
    }

    private void history(Bug bug, User actor, HistoryEventType type, String field, String oldValue, String newValue) {
        historyRepository.save(new BugHistory(bug.getId(), actor, type, field, oldValue, newValue));
    }

    /** The client must echo the version it last saw; a mismatch means someone else changed the bug first. */
    private static void assertVersion(Bug bug, Long expectedVersion) {
        if (!Objects.equals(bug.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Bug.class, bug.getId());
        }
    }

    private static void requireActive(Project project) {
        if (project.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived and read-only");
        }
    }

    private static Sort.Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.Direction.DESC;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc");
        };
    }

    /** ORDER BY a severity rank (BLOCKER=0 ... LOW=4) installed from a Specification; skipped for count queries. */
    private static Specification<Bug> orderByPriority(boolean mostSevereFirst) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                CriteriaBuilder.Case<Integer> rank = cb.selectCase();
                Expression<Integer> severity = rank
                        .when(cb.equal(root.get("priority"), BugPriority.BLOCKER), 0)
                        .when(cb.equal(root.get("priority"), BugPriority.CRITICAL), 1)
                        .when(cb.equal(root.get("priority"), BugPriority.HIGH), 2)
                        .when(cb.equal(root.get("priority"), BugPriority.MEDIUM), 3)
                        .otherwise(4);
                query.orderBy(mostSevereFirst ? cb.asc(severity) : cb.desc(severity),
                        cb.desc(root.get("bugNumber")));
            }
            return cb.conjunction();
        };
    }
}
