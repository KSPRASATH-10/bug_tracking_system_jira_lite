package com.enterprise.bts.repository;

import com.enterprise.bts.domain.ProjectMember;
import com.enterprise.bts.domain.ProjectMemberId;
import com.enterprise.bts.domain.ProjectRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    @Query("select m.projectRole from ProjectMember m "
            + "where m.id.projectId = :projectId and m.id.userId = :userId")
    Optional<ProjectRole> findRole(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("select count(m) > 0 from ProjectMember m "
            + "where m.id.projectId = :projectId and m.id.userId = :userId")
    boolean isMember(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("select m from ProjectMember m join fetch m.user "
            + "where m.id.projectId = :projectId order by m.user.fullName asc")
    List<ProjectMember> findAllByProject(@Param("projectId") UUID projectId);

    @Query("select m from ProjectMember m where m.id.projectId = :projectId and m.id.userId = :userId")
    Optional<ProjectMember> findMembership(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("select count(m) from ProjectMember m "
            + "where m.id.projectId = :projectId and m.projectRole = com.enterprise.bts.domain.ProjectRole.PROJECT_LEAD")
    long countLeads(@Param("projectId") UUID projectId);
}
