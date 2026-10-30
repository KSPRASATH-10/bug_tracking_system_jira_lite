package com.enterprise.bts.service;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Project;
import com.enterprise.bts.repository.BugRepository;
import com.enterprise.bts.repository.ProjectRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.web.dto.BugSummaryResponse;
import com.enterprise.bts.web.dto.DashboardResponse;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Role-aware dashboard: every figure is computed only over projects the caller can access. */
@Service
public class DashboardService {

    private static final int MY_BUGS_PAGE = 10;
    private static final int MY_BUGS_SCAN_LIMIT = 200;

    private final ProjectRepository projectRepository;
    private final BugRepository bugRepository;

    public DashboardService(ProjectRepository projectRepository, BugRepository bugRepository) {
        this.projectRepository = projectRepository;
        this.bugRepository = bugRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(AppUserPrincipal caller) {
        List<Project> projects = caller.isAdministrator()
                ? projectRepository.findByArchivedFalseOrderByProjectKeyAsc()
                : projectRepository.findVisibleToMember(caller.getId());
        Set<UUID> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());

        Map<BugStatus, Long> counts = new EnumMap<>(BugStatus.class);
        for (BugStatus status : BugStatus.values()) {
            counts.put(status, 0L);
        }
        List<BugSummaryResponse> mine = List.of();
        long openAssigned = 0;

        if (!projectIds.isEmpty()) {
            for (Object[] row : bugRepository.countByStatusForProjects(projectIds)) {
                counts.put((BugStatus) row[0], (Long) row[1]);
            }
            // Defence in depth: only assignments inside currently accessible, active projects are shown.
            List<Bug> assigned = bugRepository.findOpenAssignedTo(caller.getId(), PageRequest.of(0, MY_BUGS_SCAN_LIMIT))
                    .stream()
                    .filter(bug -> projectIds.contains(bug.getProject().getId()))
                    .toList();
            openAssigned = assigned.size();
            mine = assigned.stream().limit(MY_BUGS_PAGE).map(BugSummaryResponse::from).toList();
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        counts.forEach((status, count) -> byStatus.put(status.name(), count));
        return new DashboardResponse(projectIds.size(), byStatus, openAssigned, mine);
    }
}
