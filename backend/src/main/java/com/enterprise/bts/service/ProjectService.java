package com.enterprise.bts.service;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugHistory;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.HistoryEventType;
import com.enterprise.bts.domain.Project;
import com.enterprise.bts.domain.ProjectMember;
import com.enterprise.bts.domain.ProjectRole;
import com.enterprise.bts.domain.User;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.repository.BugHistoryRepository;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.repository.BugSpecifications;
import com.enterprise.bts.repository.ProjectMemberRepository;
import com.enterprise.bts.repository.ProjectRepository;
import com.enterprise.bts.repository.UserRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.web.dto.AddMemberRequest;
import com.enterprise.bts.web.dto.CreateProjectRequest;
import com.enterprise.bts.web.dto.MemberResponse;
import com.enterprise.bts.web.dto.ProjectResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final BugRepository bugRepository;
    private final BugHistoryRepository historyRepository;
    private final MarkdownSanitizer sanitizer;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          UserRepository userRepository, BugRepository bugRepository,
                          BugHistoryRepository historyRepository, MarkdownSanitizer sanitizer,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.bugRepository = bugRepository;
        this.historyRepository = historyRepository;
        this.sanitizer = sanitizer;
        this.auditService = auditService;
    }

    /** Administrators see every active project; everyone else sees only projects they belong to. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listVisible(AppUserPrincipal caller) {
        List<Project> projects = caller.isAdministrator()
                ? projectRepository.findByArchivedFalseOrderByProjectKeyAsc()
                : projectRepository.findVisibleToMember(caller.getId());
        return projects.stream()
                .map(p -> ProjectResponse.from(p, memberRepository.findRole(p.getId(), caller.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId, AppUserPrincipal caller) {
        Project project = load(projectId);
        return ProjectResponse.from(project, memberRepository.findRole(projectId, caller.getId()).orElse(null));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, AppUserPrincipal caller) {
        if (!caller.isAdministrator()) {
            throw new AccessDeniedException("Only administrators can create projects");
        }
        if (projectRepository.existsByProjectKey(request.projectKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project with this key already exists");
        }
        String name = sanitizer.sanitizePlainText(request.name(), 120, "Name");
        String description = request.description() == null || request.description().isBlank()
                ? null
                : sanitizer.sanitizeMarkdown(request.description(), 2000, "Description");
        Project project = projectRepository.saveAndFlush(
                new Project(request.projectKey(), name, description, caller.getId()));
        auditService.record("PROJECT_CREATED", "PROJECT", project.getId(), Map.of("key", project.getProjectKey()));
        return ProjectResponse.from(project, null);
    }

    @Transactional
    public void archive(UUID projectId, AppUserPrincipal caller) {
        Project project = load(projectId);
        if (project.isArchived()) {
            return;
        }
        project.archive();
        projectRepository.saveAndFlush(project);
        auditService.record("PROJECT_ARCHIVED", "PROJECT", projectId, Map.of("key", project.getProjectKey()));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID projectId) {
        load(projectId);
        return memberRepository.findAllByProject(projectId).stream().map(MemberResponse::from).toList();
    }

    /** Adds the user to the project, or changes the role if they are already a member. */
    @Transactional
    public MemberResponse upsertMember(UUID projectId, AddMemberRequest request, AppUserPrincipal caller) {
        Project project = load(projectId);
        requireActive(project);
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new NotFoundException("No active user with that email address"));

        Optional<ProjectMember> existing = memberRepository.findMembership(projectId, user.getId());
        ProjectMember member;
        String action;
        if (existing.isPresent()) {
            member = existing.get();
            ProjectRole previous = member.getProjectRole();
            if (previous == ProjectRole.PROJECT_LEAD && request.role() != ProjectRole.PROJECT_LEAD
                    && memberRepository.countLeads(projectId) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A project must keep at least one project lead");
            }
            member.setProjectRole(request.role());
            action = "PROJECT_MEMBER_ROLE_CHANGED";
        } else {
            member = memberRepository.save(new ProjectMember(project, user, request.role()));
            action = "PROJECT_MEMBER_ADDED";
        }
        if (request.role() == ProjectRole.REPORTER) {
            // Only developers and leads may hold assignments; keep that invariant when someone is (de)moted.
            unassignOpenBugs(projectId, user, caller);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userId", user.getId().toString());
        details.put("role", request.role().name());
        auditService.record(action, "PROJECT", projectId, details);
        return MemberResponse.from(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, AppUserPrincipal caller) {
        Project project = load(projectId);
        requireActive(project);
        ProjectMember member = memberRepository.findMembership(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        if (member.getProjectRole() == ProjectRole.PROJECT_LEAD && memberRepository.countLeads(projectId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project must keep at least one project lead");
        }
        // Leaving a project releases that person's open bugs so nothing stays assigned to someone without access.
        unassignOpenBugs(projectId, member.getUser(), caller);
        memberRepository.delete(member);
        auditService.record("PROJECT_MEMBER_REMOVED", "PROJECT", projectId, Map.of("userId", userId.toString()));
    }

    private void unassignOpenBugs(UUID projectId, User user, AppUserPrincipal actor) {
        Specification<Bug> spec = Specification.where(BugSpecifications.inProject(projectId))
                .and(BugSpecifications.notArchived())
                .and(BugSpecifications.assignedTo(user.getId()));
        User actorReference = userRepository.getReferenceById(actor.getId());
        for (Bug bug : bugRepository.findAll(spec)) {
            if (bug.getStatus() == BugStatus.CLOSED) {
                continue;
            }
            bug.setAssignee(null);
            historyRepository.save(new BugHistory(bug.getId(), actorReference, HistoryEventType.UNASSIGNED,
                    "assignee", user.getFullName(), null));
        }
    }

    private Project load(UUID projectId) {
        return projectRepository.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private static void requireActive(Project project) {
        if (project.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived and read-only");
        }
    }
}
