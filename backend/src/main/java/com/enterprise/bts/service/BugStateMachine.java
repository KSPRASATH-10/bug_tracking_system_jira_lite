package com.enterprise.bts.service;

import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Resolution;
import com.enterprise.bts.exception.InvalidTransitionException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the bug workflow:
 * OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED | REOPENED, CLOSED -> REOPENED, REOPENED -> IN_PROGRESS.
 * Closing requires a Resolution; any other target must not carry one (reopening therefore clears it).
 * Violations raise InvalidTransitionException (HTTP 400). The database CHECK constraint
 * ck_bugs_resolution_iff_closed is a second line of defence.
 */
@Component
public class BugStateMachine {

    private static final Map<BugStatus, List<BugStatus>> TRANSITIONS;

    static {
        EnumMap<BugStatus, List<BugStatus>> map = new EnumMap<>(BugStatus.class);
        map.put(BugStatus.OPEN, List.of(BugStatus.IN_PROGRESS));
        map.put(BugStatus.IN_PROGRESS, List.of(BugStatus.RESOLVED));
        map.put(BugStatus.RESOLVED, List.of(BugStatus.CLOSED, BugStatus.REOPENED));
        map.put(BugStatus.CLOSED, List.of(BugStatus.REOPENED));
        map.put(BugStatus.REOPENED, List.of(BugStatus.IN_PROGRESS));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /** Legal next states from {@code from}, in display order. */
    public List<BugStatus> allowedFrom(BugStatus from) {
        return from == null ? List.of() : TRANSITIONS.getOrDefault(from, List.of());
    }

    public boolean isAllowed(BugStatus from, BugStatus to) {
        return allowedFrom(from).contains(to);
    }

    public void validate(BugStatus from, BugStatus to, Resolution resolution) {
        if (from == null || to == null) {
            throw new InvalidTransitionException("Current and target status are required");
        }
        if (!isAllowed(from, to)) {
            throw new InvalidTransitionException("Illegal transition: " + from + " -> " + to);
        }
        if (to == BugStatus.CLOSED && resolution == null) {
            throw new InvalidTransitionException("A resolution is required to close a bug");
        }
        if (to != BugStatus.CLOSED && resolution != null) {
            throw new InvalidTransitionException("A resolution may only be supplied when closing a bug");
        }
    }
}
