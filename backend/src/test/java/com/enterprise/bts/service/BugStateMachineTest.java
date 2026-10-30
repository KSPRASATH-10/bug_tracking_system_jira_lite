package com.enterprise.bts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.bts.domain.BugStatus;
import com.enterprise.bts.domain.Resolution;
import com.enterprise.bts.exception.InvalidTransitionException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class BugStateMachineTest {

    /** The specification, written independently of the implementation. */
    private static final Map<BugStatus, Set<BugStatus>> LEGAL = Map.of(
            BugStatus.OPEN, EnumSet.of(BugStatus.IN_PROGRESS),
            BugStatus.IN_PROGRESS, EnumSet.of(BugStatus.RESOLVED),
            BugStatus.RESOLVED, EnumSet.of(BugStatus.CLOSED, BugStatus.REOPENED),
            BugStatus.CLOSED, EnumSet.of(BugStatus.REOPENED),
            BugStatus.REOPENED, EnumSet.of(BugStatus.IN_PROGRESS));

    private final BugStateMachine machine = new BugStateMachine();

    private static Stream<Arguments> pairs(boolean legal) {
        List<Arguments> list = new ArrayList<>();
        for (BugStatus from : BugStatus.values()) {
            for (BugStatus to : BugStatus.values()) {
                if (LEGAL.get(from).contains(to) == legal) {
                    list.add(Arguments.of(from, to));
                }
            }
        }
        return list.stream();
    }

    static Stream<Arguments> allPairs() {
        return Stream.concat(pairs(true), pairs(false));
    }

    static Stream<Arguments> legalPairs() {
        return pairs(true);
    }

    static Stream<Arguments> illegalPairs() {
        return pairs(false);
    }

    @ParameterizedTest(name = "isAllowed({0}, {1})")
    @MethodSource("allPairs")
    void isAllowedMatchesTheSpecificationForEveryOrderedPair(BugStatus from, BugStatus to) {
        assertThat(machine.isAllowed(from, to)).isEqualTo(LEGAL.get(from).contains(to));
    }

    @ParameterizedTest(name = "{0} -> {1} is accepted")
    @MethodSource("legalPairs")
    void legalTransitionsAreAccepted(BugStatus from, BugStatus to) {
        Resolution resolution = to == BugStatus.CLOSED ? Resolution.FIXED : null;
        assertThatCode(() -> machine.validate(from, to, resolution)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("illegalPairs")
    void illegalTransitionsAreRejected(BugStatus from, BugStatus to) {
        Resolution resolution = to == BugStatus.CLOSED ? Resolution.FIXED : null;
        assertThatThrownBy(() -> machine.validate(from, to, resolution))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("Illegal transition: " + from + " -> " + to);
    }

    @Test
    @DisplayName("Closing without a resolution is rejected")
    void closingRequiresAResolution() {
        assertThatThrownBy(() -> machine.validate(BugStatus.RESOLVED, BugStatus.CLOSED, null))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("resolution is required");
    }

    @ParameterizedTest(name = "closing with {0} is accepted")
    @EnumSource(Resolution.class)
    void everyResolutionValueIsAcceptedWhenClosing(Resolution resolution) {
        assertThatCode(() -> machine.validate(BugStatus.RESOLVED, BugStatus.CLOSED, resolution))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A resolution may only accompany CLOSED (reopening clears it)")
    void resolutionIsRejectedForNonClosingTransitions() {
        assertThatThrownBy(() -> machine.validate(BugStatus.OPEN, BugStatus.IN_PROGRESS, Resolution.FIXED))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("may only be supplied");
        assertThatThrownBy(() -> machine.validate(BugStatus.CLOSED, BugStatus.REOPENED, Resolution.FIXED))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("may only be supplied");
        assertThatCode(() -> machine.validate(BugStatus.CLOSED, BugStatus.REOPENED, null)).doesNotThrowAnyException();
    }

    @Test
    void nullArgumentsAreRejectedNotDereferenced() {
        assertThatThrownBy(() -> machine.validate(null, BugStatus.OPEN, null))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> machine.validate(BugStatus.OPEN, null, null))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(machine.allowedFrom(null)).isEmpty();
        assertThat(machine.isAllowed(null, BugStatus.OPEN)).isFalse();
    }

    @Test
    void allowedFromReturnsTheDisplayOrderUsedByTheUi() {
        assertThat(machine.allowedFrom(BugStatus.OPEN)).containsExactly(BugStatus.IN_PROGRESS);
        assertThat(machine.allowedFrom(BugStatus.IN_PROGRESS)).containsExactly(BugStatus.RESOLVED);
        assertThat(machine.allowedFrom(BugStatus.RESOLVED)).containsExactly(BugStatus.CLOSED, BugStatus.REOPENED);
        assertThat(machine.allowedFrom(BugStatus.CLOSED)).containsExactly(BugStatus.REOPENED);
        assertThat(machine.allowedFrom(BugStatus.REOPENED)).containsExactly(BugStatus.IN_PROGRESS);
    }

    @Test
    void allowedFromIsImmutable() {
        List<BugStatus> allowed = machine.allowedFrom(BugStatus.OPEN);
        assertThatThrownBy(() -> allowed.add(BugStatus.CLOSED)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Every status is reachable from OPEN and none is a dead end")
    void workflowGraphIsConnectedAndHasNoDeadEnds() {
        Set<BugStatus> reached = EnumSet.of(BugStatus.OPEN);
        Deque<BugStatus> queue = new ArrayDeque<>(List.of(BugStatus.OPEN));
        while (!queue.isEmpty()) {
            for (BugStatus next : machine.allowedFrom(queue.poll())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        assertThat(reached).containsExactlyInAnyOrder(BugStatus.values());
        for (BugStatus status : BugStatus.values()) {
            assertThat(machine.allowedFrom(status)).as("outgoing transitions from %s", status).isNotEmpty();
        }
    }
}
