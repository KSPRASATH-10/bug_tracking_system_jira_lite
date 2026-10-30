package com.enterprise.bts.repository;

import com.enterprise.bts.domain.BugComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugCommentRepository extends JpaRepository<BugComment, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<BugComment> findByBugIdOrderByCreatedAtAsc(UUID bugId);
}
