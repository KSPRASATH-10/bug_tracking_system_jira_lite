package com.enterprise.bts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Lost-update protection. Two layers are exercised: the explicit version check (stale client copy -> 409) and
 * Hibernate's @Version + the row lock (genuinely simultaneous writers that both read the same version).
 */
class OptimisticLockingIntegrationTest extends AbstractIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String BUG = "/api/projects/{p}/bugs/{b}";

    private record Outcome(int status, String body) {
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome outcomeOf(MockHttpServletResponse response) throws Exception {
        return new Outcome(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------ stale client copy

    @Test
    void aStaleVersionIsRejectedWith409OnEveryWriteEndpoint() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("stale"), "LOW");
        UUID bugId = id(bug);
        long version0 = bug.get("version").asLong();
        Cookie dev = cookieFor(DEV_ALPHA);
        Cookie lead = cookieFor(LEAD_ALPHA);

        JsonNode edited = body(perform(dev, patch(BUG, ALPHA, bugId).contentType(JSON)
                .content(json("title", unique("first-edit"), "version", version0))).andExpect(status().isOk()));
        assertThat(edited.get("version").asLong()).isGreaterThan(version0);

        // A second client still holding the old copy loses, on PATCH, transition and assign alike.
        assertError(perform(dev, patch(BUG, ALPHA, bugId).contentType(JSON)
                .content(json("title", unique("second-edit"), "version", version0))), 409, "CONCURRENT_MODIFICATION");
        assertError(transitionRaw(dev, ALPHA, bugId, "IN_PROGRESS", null, version0), 409, "CONCURRENT_MODIFICATION");
        assertError(perform(lead, put(BUG + "/assignee", ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", DEV_ALPHA_ID, "version", version0))), 409, "CONCURRENT_MODIFICATION");

        // The loser's changes were not applied; the winner's were.
        perform(dev, get(BUG, ALPHA, bugId))
                .andExpect(jsonPath("$.title").value(edited.get("title").asText()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").doesNotExist());

        // Retrying with the fresh version succeeds.
        transitionRaw(dev, ALPHA, bugId, "IN_PROGRESS", null, edited.get("version").asLong())
                .andExpect(status().isOk());
    }

    @Test
    void theVersionFieldIsMandatory() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("noversion"), "LOW");
        assertError(perform(cookieFor(DEV_ALPHA), patch(BUG, ALPHA, id(bug)).contentType(JSON)
                .content("{\"title\":\"no version supplied\"}")), 400, "VALIDATION_FAILED");
        assertError(perform(cookieFor(LEAD_ALPHA), put(BUG + "/assignee", ALPHA, id(bug)).contentType(JSON)
                .content("{\"assigneeId\":null}")), 400, "VALIDATION_FAILED");
    }

    // ------------------------------------------------------------ genuinely simultaneous writers

    @Test
    void whenTwoUsersTransitionTheSameVersionAtOnceExactlyOneWins() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        Cookie lead = cookieFor(LEAD_ALPHA);

        for (int round = 0; round < 5; round++) {
            JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("race-transition"), "HIGH");
            UUID bugId = id(bug);
            long version = bug.get("version").asLong();

            List<Outcome> outcomes = runConcurrently(2, () -> outcomeOf(
                    transitionRaw(round(dev, lead), ALPHA, bugId, "IN_PROGRESS", null, version)
                            .andReturn().getResponse()));

            assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(200, 409);
            Outcome loser = outcomes.stream().filter(o -> o.status() == 409).findFirst().orElseThrow();
            assertThat(objectMapper.readTree(loser.body()).get("code").asText()).isEqualTo("CONCURRENT_MODIFICATION");

            perform(dev, get(BUG, ALPHA, bugId)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
            // The losing transaction rolled back completely: one history row, one audit row.
            assertThat(jdbc.queryForObject(
                    "select count(*) from bug_history where bug_id = ? and event_type = 'STATUS_CHANGED'",
                    Long.class, bugId)).isEqualTo(1);
            assertThat(auditCount("BUG_STATUS_CHANGED", bugId)).isEqualTo(1);
        }
    }

    /** Alternates the acting user so both a developer and a lead are used as competing writers. */
    private int turn = 0;

    private synchronized Cookie round(Cookie first, Cookie second) {
        return (turn++ % 2 == 0) ? first : second;
    }

    @Test
    void anEditAndATransitionRacingOnTheSameVersionCannotBothSucceed() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);

        for (int round = 0; round < 5; round++) {
            JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("race-mixed"), "MEDIUM");
            UUID bugId = id(bug);
            long version = bug.get("version").asLong();
            List<Callable<Outcome>> writers = List.of(
                    () -> outcomeOf(transitionRaw(dev, ALPHA, bugId, "IN_PROGRESS", null, version)
                            .andReturn().getResponse()),
                    () -> outcomeOf(perform(dev, patch(BUG, ALPHA, bugId).contentType(JSON)
                            .content(json("title", unique("raced-title"), "version", version)))
                            .andReturn().getResponse()));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Outcome> outcomes = new ArrayList<>();
            try {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Outcome>> futures = new ArrayList<>();
                for (Callable<Outcome> writer : writers) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return writer.call();
                    }));
                }
                ready.await();
                go.countDown();
                for (Future<Outcome> future : futures) {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(200, 409);
            JsonNode finalBug = body(perform(dev, get(BUG, ALPHA, bugId)).andExpect(status().isOk()));
            assertThat(finalBug.get("version").asLong()).as("exactly one write was applied").isEqualTo(version + 1);
        }
    }

    // ------------------------------------------------------------ bug numbering under concurrency

    @Test
    void concurrentBugCreationNeverProducesDuplicateBugNumbers() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        int writers = 6;

        List<Outcome> outcomes = runConcurrently(writers, () -> outcomeOf(
                perform(dev, post("/api/projects/{p}/bugs", ALPHA).contentType(JSON)
                        .content(json("title", unique("numbering"), "priority", "LOW")))
                        .andReturn().getResponse()));

        Set<String> keys = new HashSet<>();
        for (Outcome outcome : outcomes) {
            assertThat(outcome.status()).as(outcome.body()).isEqualTo(201);
            keys.add(objectMapper.readTree(outcome.body()).get("key").asText());
        }
        assertThat(keys).hasSize(writers);

        // The database agrees: (project_id, bug_number) is unique across everything ever created.
        assertThat(jdbc.queryForObject(
                "select count(*) from (select project_id, bug_number from bugs group by 1, 2 having count(*) > 1) d",
                Long.class)).isZero();
    }
}
