package com.enterprise.bts.repository;

import com.enterprise.bts.domain.Bug;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public interface BugRepository extends JpaRepository<Bug, UUID>, JpaSpecificationExecutor<Bug> {

    /**
     * BOLA/IDOR guard at the query level: a bug is only ever resolved WITHIN the project the caller
     * was authorised for. A bug id belonging to another project simply yields an empty result.
     */
    @EntityGraph(attributePaths = {"project", "reporter", "assignee"})
    @Query("select b from Bug b where b.id = :bugId and b.project.id = :projectId and b.archived = false")
    Optional<Bug> findActiveInProject(@Param("bugId") UUID bugId, @Param("projectId") UUID projectId);

    @Override
    @EntityGraph(attributePaths = {"project", "reporter", "assignee"})
    Page<Bug> findAll(@Nullable Specification<Bug> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "reporter", "assignee"})
    @Query("""
            select b from Bug b
            where b.assignee.id = :userId and b.archived = false
              and b.status <> com.enterprise.bts.domain.BugStatus.CLOSED
            order by b.updatedAt desc
            """)
    List<Bug> findOpenAssignedTo(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select count(b) from Bug b
            where b.assignee.id = :userId and b.archived = false
              and b.status <> com.enterprise.bts.domain.BugStatus.CLOSED
            """)
    long countOpenAssignedTo(@Param("userId") UUID userId);

    /** Rows of [BugStatus, Long] restricted to the given projects. */
    @Query("select b.status, count(b) from Bug b "
            + "where b.archived = false and b.project.id in :projectIds group by b.status")
    List<Object[]> countByStatusForProjects(@Param("projectIds") Collection<UUID> projectIds);

    /** Rows of [BugStatus, Long] across every non-archived bug (administrators only). */
    @Query("select b.status, count(b) from Bug b where b.archived = false group by b.status")
    List<Object[]> countByStatusAll();
}
