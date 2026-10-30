package com.enterprise.bts.repository;

import com.enterprise.bts.domain.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @EntityGraph(attributePaths = {"uploader"})
    List<Attachment> findByBugIdAndArchivedFalseOrderByCreatedAtAsc(UUID bugId);

    /** Scoped to the bug (and therefore the project) the caller was authorised for. */
    @EntityGraph(attributePaths = {"uploader"})
    @Query("select a from Attachment a where a.id = :id and a.bugId = :bugId and a.archived = false")
    Optional<Attachment> findActiveInBug(@Param("id") UUID id, @Param("bugId") UUID bugId);
}
