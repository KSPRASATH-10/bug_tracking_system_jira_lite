package com.enterprise.bts.security;

import com.enterprise.bts.domain.ProjectRole;
import com.enterprise.bts.repository.ProjectMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Project-scoped authorisation decisions, referenced from method security, e.g.
 * {@code @PreAuthorize("@projectSecurity.canAccessProject(#projectId, authentication)")}.
 *
 * This is the BOLA/IDOR gate: membership is looked up in the database on every call, never trusted from
 * the client or the token. Every method fails CLOSED (false) for a missing id or an unauthenticated
 * caller. A non-member receives the same 403 whether or not the project exists, so project ids
 * cannot be enumerated.
 */
@Component("projectSecurity")
public class ProjectSecurity {

    private final ProjectMemberRepository memberRepository;

    public ProjectSecurity(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** Any member (REPORTER and up) or a system administrator. */
    public boolean canAccessProject(UUID projectId, Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        if (principal == null || projectId == null) {
            return false;
        }
        return principal.isAdministrator() || memberRepository.isMember(projectId, principal.getId());
    }

    /** DEVELOPER, PROJECT_LEAD or administrator: may transition and edit bugs. */
    public boolean isDeveloperOrLead(UUID projectId, Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        if (principal == null || projectId == null) {
            return false;
        }
        if (principal.isAdministrator()) {
            return true;
        }
        return memberRepository.findRole(projectId, principal.getId())
                .map(role -> role == ProjectRole.DEVELOPER || role == ProjectRole.PROJECT_LEAD)
                .orElse(false);
    }

    /** PROJECT_LEAD or administrator: may assign bugs and manage members. */
    public boolean isLead(UUID projectId, Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        if (principal == null || projectId == null) {
            return false;
        }
        if (principal.isAdministrator()) {
            return true;
        }
        return memberRepository.findRole(projectId, principal.getId())
                .map(role -> role == ProjectRole.PROJECT_LEAD)
                .orElse(false);
    }

    public boolean isAdministrator(Authentication authentication) {
        AppUserPrincipal principal = principal(authentication);
        return principal != null && principal.isAdministrator();
    }

    /** The caller's explicit project role, empty for non-members (including administrators who are not members). */
    public Optional<ProjectRole> roleOf(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            return Optional.empty();
        }
        return memberRepository.findRole(projectId, userId);
    }

    private static AppUserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
