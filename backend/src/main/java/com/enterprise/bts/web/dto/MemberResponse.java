package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.ProjectMember;
import com.enterprise.bts.domain.ProjectRole;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, String fullName, ProjectRole role) {

    public static MemberResponse from(ProjectMember member) {
        return new MemberResponse(member.getUserId(), member.getUser().getEmail(),
                member.getUser().getFullName(), member.getProjectRole());
    }
}
