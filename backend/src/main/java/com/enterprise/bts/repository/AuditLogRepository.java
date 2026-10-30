package com.enterprise.bts.repository;

import com.enterprise.bts.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Insert-only in practice: the entity is @Immutable and the table rejects UPDATE/DELETE/TRUNCATE. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
