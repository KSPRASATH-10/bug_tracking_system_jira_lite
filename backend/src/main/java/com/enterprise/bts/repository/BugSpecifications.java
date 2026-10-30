package com.enterprise.bts.repository;

import com.enterprise.bts.domain.Bug;
import com.enterprise.bts.domain.BugPriority;
import com.enterprise.bts.domain.BugStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;

/**
 * Type-safe query building (Criteria API): every value is a bound parameter, so SQL injection through
 * filter or search input is structurally impossible. Methods return null for "no filter", which
 * Specification.where(..).and(..) treats as a no-op.
 */
public final class BugSpecifications {

    /** Matches "42" or "ALPHA-42". */
    private static final Pattern KEY_OR_NUMBER = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9]{1,9}-)?(\\d{1,9})$");
    private static final char LIKE_ESCAPE = '\\';

    private BugSpecifications() {
    }

    public static Specification<Bug> inProject(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Bug> notArchived() {
        return (root, query, cb) -> cb.isFalse(root.get("archived"));
    }

    public static Specification<Bug> hasStatus(BugStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Bug> hasPriority(BugPriority priority) {
        if (priority == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Bug> assignedTo(UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    /** Case-insensitive title search; "ALPHA-12" or "12" additionally matches bug number 12. */
    public static Specification<Bug> matchesText(String rawSearch) {
        if (rawSearch == null || rawSearch.isBlank()) {
            return null;
        }
        final String term = rawSearch.trim();
        final String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        final Integer number = parseBugNumber(term);
        return (root, query, cb) -> {
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE);
            if (number == null) {
                return titleMatch;
            }
            return cb.or(titleMatch, cb.equal(root.get("bugNumber"), number));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static Integer parseBugNumber(String term) {
        Matcher m = KEY_OR_NUMBER.matcher(term);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }
}
