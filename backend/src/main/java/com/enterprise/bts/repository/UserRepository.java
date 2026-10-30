package com.enterprise.bts.repository;

import com.enterprise.bts.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lower-cased (DB CHECK); callers must normalise before lookup. */
    Optional<User> findByEmail(String email);
}
