package com.enterprise.bts.repository;

import com.enterprise.bts.domain.BugHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugHistoryRepository extends JpaRepository<BugHistory, UUID> {

    @EntityGraph(attributePaths = {"actor"})
    List<BugHistory> findByBugIdOrderByCreatedAtAsc(UUID bugId);
}
