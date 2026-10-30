package com.enterprise.bts.service;

import com.enterprise.bts.domain.AuditLog;
import com.enterprise.bts.repository.AuditLogRepository;
import com.enterprise.bts.security.AppUserPrincipal;
import com.enterprise.bts.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the append-only audit ledger. Business events join the caller's transaction (so an audit row
 * can never describe a change that was rolled back, e.g. after an optimistic-lock failure); calls
 * made outside a transaction (security filters, exception handlers) get their own. Never pass
 * secrets, tokens, passwords or free-form user input as details.
 */
@Service
public class AuditService {

    private static final int MAX_DETAIL_VALUE_LENGTH = 500;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an event attributed to the currently authenticated user (null actor if anonymous). */
    @Transactional
    public void record(String action, String entityType, UUID entityId, Map<String, Object> details) {
        write(currentActorId(), action, entityType, entityId, details);
    }

    /** Records an event with an explicit actor (e.g. login, where no SecurityContext exists yet). */
    @Transactional
    public void recordAs(UUID actorId, String action, String entityType, UUID entityId,
                         Map<String, Object> details) {
        write(actorId, action, entityType, entityId, details);
    }

    private void write(UUID actorId, String action, String entityType, UUID entityId,
                       Map<String, Object> details) {
        repository.save(new AuditLog(
                CorrelationIdFilter.current(),
                actorId,
                truncate(action, 60),
                truncate(entityType, 40),
                entityId,
                boundedDetails(details),
                currentRemoteIp()));
    }

    private static Map<String, Object> boundedDetails(Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> bounded.put(truncate(key, 60),
                    value instanceof String text ? truncate(text, MAX_DETAIL_VALUE_LENGTH) : value));
        }
        return bounded;
    }

    private static UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private static String currentRemoteIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return truncate(request.getRemoteAddr(), 45);
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
