package com.enterprise.bts.repository;

import com.enterprise.bts.domain.Project;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByProjectKey(String projectKey);

    /** Row-locks the project so concurrent bug creations are serialised and get distinct bug numbers. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);

    List<Project> findByArchivedFalseOrderByProjectKeyAsc();

    @Query("""
            select p from Project p
            where p.archived = false
              and exists (select 1 from ProjectMember m
                          where m.id.projectId = p.id and m.id.userId = :userId)
            order by p.projectKey asc
            """)
    List<Project> findVisibleToMember(@Param("userId") UUID userId);
}
