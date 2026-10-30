package com.enterprise.bts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.bts.config.BtsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class BugWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String BUGS = "/api/projects/{p}/bugs";
    private static final String BUG = "/api/projects/{p}/bugs/{b}";

    @Autowired
    private BtsProperties properties;

    // ------------------------------------------------------------ full lifecycle

    @Test
    void aBugTravelsThroughTheWholeWorkflowIncludingCloseAndReopen() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("lifecycle"), "HIGH");
        assertThat(bug.get("status").asText()).isEqualTo("OPEN");
        assertThat(bug.get("version").asLong()).isZero();
        assertThat(bug.get("key").asText()).startsWith("ALPHA-");
        assertThat(bug.get("reporter").get("email").asText()).isEqualTo(DEV_ALPHA);
        assertThat(texts(bug.get("allowedTransitions"))).containsExactly("IN_PROGRESS");

        JsonNode assigned = body(perform(cookieFor(LEAD_ALPHA), put(BUG + "/assignee", ALPHA, id(bug)).contentType(JSON)
                .content(json("assigneeId", DEV_ALPHA_ID, "version", bug.get("version").asLong())))
                .andExpect(status().isOk()));
        assertThat(assigned.get("assignee").get("email").asText()).isEqualTo(DEV_ALPHA);
        assertThat(assigned.get("version").asLong()).isGreaterThan(bug.get("version").asLong());

        JsonNode inProgress = transition(DEV_ALPHA, assigned, "IN_PROGRESS", null);
        assertThat(texts(inProgress.get("allowedTransitions"))).containsExactly("RESOLVED");

        JsonNode resolved = transition(DEV_ALPHA, inProgress, "RESOLVED", null);
        assertThat(texts(resolved.get("allowedTransitions"))).containsExactly("CLOSED", "REOPENED");

        JsonNode closed = transition(LEAD_ALPHA, resolved, "CLOSED", "FIXED");
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("resolution").asText()).isEqualTo("FIXED");
        assertThat(texts(closed.get("allowedTransitions"))).containsExactly("REOPENED");

        JsonNode reopened = transition(LEAD_ALPHA, closed, "REOPENED", null);
        assertThat(reopened.get("status").asText()).isEqualTo("REOPENED");
        assertThat(reopened.get("resolution").isNull()).as("reopening clears the resolution").isTrue();
        assertThat(texts(reopened.get("allowedTransitions"))).containsExactly("IN_PROGRESS");

        JsonNode again = transition(DEV_ALPHA, reopened, "IN_PROGRESS", null);
        assertThat(again.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(again.get("version").asLong()).isGreaterThan(reopened.get("version").asLong());
    }

    @Test
    void everyBugChangeProducesAnAppendOnlyHistoryRowAndAnAuditRow() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("ledger"), "LOW");
        UUID bugId = id(bug);
        JsonNode inProgress = transition(DEV_ALPHA, bug, "IN_PROGRESS", null);
        JsonNode resolved = transition(DEV_ALPHA, inProgress, "RESOLVED", null);
        transition(LEAD_ALPHA, resolved, "CLOSED", "WONT_FIX");

        Long statusChanges = jdbc.queryForObject(
                "select count(*) from bug_history where bug_id = ? and event_type = 'STATUS_CHANGED'", Long.class, bugId);
        assertThat(statusChanges).isEqualTo(3);
        assertThat(auditCount("BUG_CREATED", bugId)).isEqualTo(1);
        assertThat(auditCount("BUG_STATUS_CHANGED", bugId)).isEqualTo(3);
        String resolutionEvent = jdbc.queryForObject(
                "select new_value from bug_history where bug_id = ? and field_name = 'resolution'", String.class, bugId);
        assertThat(resolutionEvent).isEqualTo("WONT_FIX");
    }

    @Test
    void auditRecordsShareTheCorrelationIdOfTheRequestThatCausedThem() throws Exception {
        ResultActions created = perform(cookieFor(DEV_ALPHA), post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", unique("cid"), "priority", "LOW"))).andExpect(status().isCreated());
        String headerValue = created.andReturn().getResponse().getHeader("X-Correlation-Id");
        UUID bugId = id(body(created));

        String stored = jdbc.queryForObject(
                "select correlation_id from audit_log where action = 'BUG_CREATED' and entity_id = ?", String.class, bugId);
        assertThat(stored).isEqualTo(headerValue);
    }

    // ------------------------------------------------------------ illegal transitions

    @Test
    void illegalTransitionsAreRejectedWithHttp400AndChangeNothing() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("illegal"), "MEDIUM");
        Cookie dev = cookieFor(DEV_ALPHA);
        long version = bug.get("version").asLong();

        for (String target : List.of("OPEN", "RESOLVED", "REOPENED")) {
            assertError(transitionRaw(dev, ALPHA, id(bug), target, null, version), 400, "INVALID_TRANSITION");
        }
        assertError(transitionRaw(dev, ALPHA, id(bug), "CLOSED", "FIXED", version), 400, "INVALID_TRANSITION");

        perform(dev, get(BUG, ALPHA, id(bug)))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void closingRequiresAResolutionAndOtherTransitionsForbidOne() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("resolution"), "MEDIUM");
        JsonNode resolved = transition(DEV_ALPHA, transition(DEV_ALPHA, bug, "IN_PROGRESS", null), "RESOLVED", null);
        Cookie lead = cookieFor(LEAD_ALPHA);
        long version = resolved.get("version").asLong();

        perform(lead, post(BUG + "/transition", ALPHA, id(resolved)).contentType(JSON)
                .content(json("targetStatus", "CLOSED", "version", version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").value("A resolution is required to close a bug"));
        perform(lead, post(BUG + "/transition", ALPHA, id(resolved)).contentType(JSON)
                .content(json("targetStatus", "REOPENED", "resolution", "FIXED", "version", version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void malformedTransitionPayloadsAreRejected() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("payload"), "LOW");
        Cookie dev = cookieFor(DEV_ALPHA);
        String path = BUG + "/transition";

        assertError(perform(dev, post(path, ALPHA, id(bug)).contentType(JSON)
                .content("{\"targetStatus\":\"DONE\",\"version\":0}")), 400, "MALFORMED_REQUEST");
        assertError(perform(dev, post(path, ALPHA, id(bug)).contentType(JSON)
                .content("{\"targetStatus\":\"IN_PROGRESS\"}")), 400, "VALIDATION_FAILED");
        assertError(perform(dev, post(path, ALPHA, id(bug)).contentType(JSON)
                .content("{\"version\":0}")), 400, "VALIDATION_FAILED");
        assertError(perform(dev, post(path, ALPHA, id(bug)).contentType(JSON).content("not json")),
                400, "MALFORMED_REQUEST");
    }

    @Test
    void theDatabaseItselfRefusesInconsistentStatusAndResolution() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("dbcheck"), "LOW"));

        // Bypassing the service: CLOSED without a resolution, and a resolution without CLOSED, are both impossible.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("update bugs set status = 'CLOSED' where id = ?", bugId))
                .isInstanceOf(DataAccessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("update bugs set resolution = 'FIXED' where id = ?", bugId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ledgerTablesAreAppendOnlyAndBusinessTablesAreNeverHardDeleted() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("immutable"), "LOW"));
        perform(cookieFor(DEV_ALPHA), post(BUG + "/comments", ALPHA, bugId).contentType(JSON)
                .content("{\"body\":\"first\"}")).andExpect(status().isCreated());

        String[] forbidden = {
                "update bug_history set new_value = 'tampered' where bug_id = '" + bugId + "'",
                "delete from bug_history where bug_id = '" + bugId + "'",
                "update bug_comments set body = 'tampered' where bug_id = '" + bugId + "'",
                "delete from bug_comments where bug_id = '" + bugId + "'",
                "update audit_log set action = 'TAMPERED' where entity_id = '" + bugId + "'",
                "delete from audit_log where entity_id = '" + bugId + "'",
                "truncate table bug_comments",
                "truncate table bug_history",
                "truncate table audit_log",
                "delete from bugs where id = '" + bugId + "'",
                "delete from projects where id = '" + ALPHA + "'",
                "delete from users where id = '" + DEV_ALPHA_ID + "'"};
        for (String sql : forbidden) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.execute(sql))
                    .as(sql).isInstanceOf(DataAccessException.class);
        }
        assertThat(jdbc.queryForObject("select count(*) from bug_history where bug_id = ?", Long.class, bugId))
                .isPositive();
    }

    // ------------------------------------------------------------ list: pagination, sorting, filters, search

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("content").forEach(bug -> titles.add(bug.get("title").asText()));
        return titles;
    }

    @Test
    void listSupportsServerSidePaginationSortingFilteringAndSearch() throws Exception {
        String token = unique("pg");
        createBug(DEV_ALPHA, ALPHA, token + " A item", "LOW");
        createBug(DEV_ALPHA, ALPHA, token + " B item", "BLOCKER");
        createBug(DEV_ALPHA, ALPHA, token + " C item", "MEDIUM");
        Cookie reporter = cookieFor(REPORTER_ALPHA);

        JsonNode page0 = body(perform(reporter, get(BUGS, ALPHA).param("search", token).param("size", "2")
                .param("page", "0").param("sort", "title").param("direction", "asc")).andExpect(status().isOk()));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("size").asInt()).isEqualTo(2);
        assertThat(titles(page0)).containsExactly(token + " A item", token + " B item");

        JsonNode page1 = body(perform(reporter, get(BUGS, ALPHA).param("search", token).param("size", "2")
                .param("page", "1").param("sort", "title").param("direction", "asc")).andExpect(status().isOk()));
        assertThat(titles(page1)).containsExactly(token + " C item");

        // Severity order, not alphabetical: BLOCKER, MEDIUM, LOW.
        JsonNode bySeverity = body(perform(reporter, get(BUGS, ALPHA).param("search", token.toUpperCase())
                .param("sort", "priority").param("direction", "desc")).andExpect(status().isOk()));
        assertThat(titles(bySeverity)).containsExactly(token + " B item", token + " C item", token + " A item");
        JsonNode leastSevereFirst = body(perform(reporter, get(BUGS, ALPHA).param("search", token)
                .param("sort", "priority").param("direction", "asc")).andExpect(status().isOk()));
        assertThat(titles(leastSevereFirst)).containsExactly(token + " A item", token + " C item", token + " B item");

        JsonNode blockers = body(perform(reporter, get(BUGS, ALPHA).param("search", token)
                .param("priority", "BLOCKER")).andExpect(status().isOk()));
        assertThat(titles(blockers)).containsExactly(token + " B item");
        JsonNode inProgress = body(perform(reporter, get(BUGS, ALPHA).param("search", token)
                .param("status", "IN_PROGRESS")).andExpect(status().isOk()));
        assertThat(inProgress.get("totalElements").asLong()).isZero();
    }

    @Test
    void searchMatchesBugKeysAndAssigneeFilterWorks() throws Exception {
        Cookie reporter = cookieFor(REPORTER_ALPHA);
        JsonNode byKey = body(perform(reporter, get(BUGS, ALPHA).param("search", "ALPHA-1")).andExpect(status().isOk()));
        List<String> keys = new ArrayList<>();
        byKey.get("content").forEach(bug -> keys.add(bug.get("key").asText()));
        assertThat(keys).contains("ALPHA-1");

        JsonNode assigned = body(perform(reporter, get(BUGS, ALPHA).param("assigneeId", DEV_ALPHA_ID.toString())
                .param("status", "IN_PROGRESS")).andExpect(status().isOk()));
        assertThat(assigned.get("content")).isNotEmpty();
        assigned.get("content").forEach(bug ->
                assertThat(bug.get("assigneeId").asText()).isEqualTo(DEV_ALPHA_ID.toString()));
    }

    @Test
    void sortingAndPagingParametersAreStrictlyAllowListed() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        assertError(perform(dev, get(BUGS, ALPHA).param("sort", "reporter.passwordHash")), 400, "BAD_REQUEST");
        assertError(perform(dev, get(BUGS, ALPHA).param("sort", "title;drop table bugs")), 400, "BAD_REQUEST");
        assertError(perform(dev, get(BUGS, ALPHA).param("direction", "sideways")), 400, "BAD_REQUEST");
        assertError(perform(dev, get(BUGS, ALPHA).param("status", "NOPE")), 400, "INVALID_PARAMETER");

        perform(dev, get(BUGS, ALPHA).param("size", "1000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        perform(dev, get(BUGS, ALPHA).param("page", "-5").param("size", "0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
    }

    // ------------------------------------------------------------ edit, archive

    @Test
    void developersEditTitleDescriptionAndPriorityAndEveryEditIsRecorded() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("edit"), "LOW");
        String newTitle = unique("edited");

        JsonNode updated = body(perform(cookieFor(DEV_ALPHA), patch(BUG, ALPHA, id(bug)).contentType(JSON)
                .content(json("title", newTitle, "description", "New **description**", "priority", "CRITICAL",
                        "version", bug.get("version").asLong()))).andExpect(status().isOk()));
        assertThat(updated.get("title").asText()).isEqualTo(newTitle);
        assertThat(updated.get("priority").asText()).isEqualTo("CRITICAL");
        assertThat(updated.get("version").asLong()).isGreaterThan(bug.get("version").asLong());

        JsonNode timeline = body(perform(cookieFor(DEV_ALPHA), get(BUG + "/timeline", ALPHA, id(bug)))
                .andExpect(status().isOk()));
        List<String> changedFields = new ArrayList<>();
        timeline.forEach(entry -> {
            if ("UPDATED".equals(entry.path("eventType").asText())) {
                changedFields.add(entry.get("fieldName").asText());
            }
        });
        assertThat(changedFields).contains("title", "description", "priority");
    }

    @Test
    void archivingIsASoftDeleteThatHidesTheBugEverywhere() throws Exception {
        String token = unique("archive");
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, token + " item", "LOW");
        UUID bugId = id(bug);
        Cookie lead = cookieFor(LEAD_ALPHA);

        perform(lead, delete(BUG, ALPHA, bugId)).andExpect(status().isNoContent());

        assertError(perform(lead, get(BUG, ALPHA, bugId)), 404, "NOT_FOUND");
        assertError(perform(lead, get(BUG + "/timeline", ALPHA, bugId)), 404, "NOT_FOUND");
        assertError(perform(lead, delete(BUG, ALPHA, bugId)), 404, "NOT_FOUND");
        perform(lead, get(BUGS, ALPHA).param("search", token)).andExpect(jsonPath("$.totalElements").value(0));
        // The row, its history and the audit trail are all still in the database.
        assertThat(jdbc.queryForObject("select archived from bugs where id = ?", Boolean.class, bugId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from bug_history where bug_id = ?", Long.class, bugId)).isPositive();
        assertThat(auditCount("BUG_ARCHIVED", bugId)).isEqualTo(1);
    }

    // ------------------------------------------------------------ comments and timeline

    @Test
    void commentsAreSanitisedAppendOnlyAndAppearOnceInTheTimeline() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("comments"), "LOW");
        UUID bugId = id(bug);
        Cookie dev = cookieFor(DEV_ALPHA);

        JsonNode comment = body(perform(dev, post(BUG + "/comments", ALPHA, bugId).contentType(JSON)
                .content(json("body", "**bold** and <script>alert(1)</script> [x](javascript:alert(1))")))
                .andExpect(status().isCreated()));
        assertThat(comment.get("body").asText())
        .isEqualTo("**bold** and &lt;script>alert(1)&lt;/script> [x](#blocked))");
        assertThat(comment.get("author").get("email").asText()).isEqualTo(DEV_ALPHA);

        assertError(perform(dev, post(BUG + "/comments", ALPHA, bugId).contentType(JSON).content("{\"body\":\"   \"}")),
                400, "VALIDATION_FAILED");
        assertError(perform(dev, post(BUG + "/comments", ALPHA, bugId).contentType(JSON)
                .content(json("body", "x".repeat(5001)))), 400, "VALIDATION_FAILED");

        JsonNode timeline = body(perform(dev, get(BUG + "/timeline", ALPHA, bugId)).andExpect(status().isOk()));
        long commentEntries = 0;
        for (JsonNode entry : timeline) {
            assertThat(entry.path("eventType").asText()).isNotEqualTo("COMMENTED");
            if ("COMMENT".equals(entry.get("kind").asText())) {
                commentEntries++;
                assertThat(entry.get("body").asText()).isEqualTo(comment.get("body").asText());
            }
        }
        assertThat(commentEntries).isEqualTo(1);

        // There is no way to edit or delete a comment through the API.
        perform(dev, put(BUG + "/comments", ALPHA, bugId).contentType(JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isMethodNotAllowed());
        perform(dev, delete(BUG + "/comments/{c}", ALPHA, bugId, id(comment))).andExpect(status().isNotFound());
    }

    @Test
    void theTimelineIsChronologicalWithTheCreationEventFirst() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("timeline"), "LOW");
        JsonNode inProgress = transition(DEV_ALPHA, bug, "IN_PROGRESS", null);
        perform(cookieFor(REPORTER_ALPHA), post(BUG + "/comments", ALPHA, id(bug)).contentType(JSON)
                .content("{\"body\":\"Still happens\"}")).andExpect(status().isCreated());
        transition(DEV_ALPHA, inProgress, "RESOLVED", null);

        JsonNode timeline = body(perform(cookieFor(REPORTER_ALPHA), get(BUG + "/timeline", ALPHA, id(bug)))
                .andExpect(status().isOk()));
        assertThat(timeline.get(0).get("eventType").asText()).isEqualTo("CREATED");
        Instant previous = Instant.MIN;
        boolean sawInProgress = false;
        for (JsonNode entry : timeline) {
            Instant current = Instant.parse(entry.get("createdAt").asText());
            assertThat(current).isAfterOrEqualTo(previous);
            previous = current;
            sawInProgress |= "IN_PROGRESS".equals(entry.path("newValue").asText());
        }
        assertThat(sawInProgress).isTrue();
    }

    // ------------------------------------------------------------ attachments

    private ResultActions upload(String email, UUID bugId, String filename, String contentType, byte[] content)
            throws Exception {
        return perform(cookieFor(email), multipart("/api/projects/{p}/bugs/{b}/attachments", ALPHA, bugId)
                .file(new MockMultipartFile("file", filename, contentType, content)));
    }

    @Test
    void validUploadsAreStoredUnderOpaqueNamesAndServedAsAttachmentsOnly() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("files"), "LOW"));
        byte[] png = pngBytes();

        JsonNode attachment = body(upload(DEV_ALPHA, bugId, "diagram.png", "image/png", png)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("diagram.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.storageName").doesNotExist())
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.hasLength(64))));

        perform(cookieFor(REPORTER_ALPHA), get("/api/projects/{p}/bugs/{b}/attachments", ALPHA, bugId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        MockHttpServletResponse download = perform(cookieFor(REPORTER_ALPHA),
                get("/api/projects/{p}/bugs/{b}/attachments/{a}/content", ALPHA, bugId, id(attachment)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png))
                .andReturn().getResponse();
        assertThat(download.getHeader("Content-Disposition")).startsWith("attachment;").contains("filename=\"diagram.png\"");
        assertThat(download.getContentType()).isEqualTo("image/png");

        // On disk: only UUID names, never the user's filename, and never inside a served directory.
        Path uploadDir = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        try (Stream<Path> files = Files.list(uploadDir)) {
            files.forEach(file -> {
                assertThat(isUuid(file.getFileName().toString())).as(file.getFileName().toString()).isTrue();
            });
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ATTACHMENT_ADDED' "
                + "and details ->> 'filename' = 'diagram.png'", Long.class)).isPositive();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Test
    void dangerousOrDisguisedUploadsAreRejectedAndNothingIsStored() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("badfiles"), "LOW"));
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] windowsExe = bytes('M', 'Z', 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, 0xFF, 0xFF, 0, 0);
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(pngBytes(), 0, oversized, 0, pngBytes().length);

        assertError(upload(DEV_ALPHA, bugId, "evil.html", "text/html", html), 400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "run.sh", "text/plain", "echo hi".getBytes(StandardCharsets.UTF_8)),
                400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "shell.jsp", "text/plain", "x".getBytes(StandardCharsets.UTF_8)),
                400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "photo.png", "image/png", windowsExe), 400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "notes.txt", "text/plain", html), 400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "shot.png", "text/html", pngBytes()), 400, "FILE_REJECTED");
        assertError(upload(DEV_ALPHA, bugId, "empty.txt", "text/plain", new byte[0]), 400, "FILE_REJECTED");
        // MockMvc does not apply the servlet multipart cap; the service-level 5 MB check rejects it with 400.
        // Through a real server the container rejects it first with 413 FILE_TOO_LARGE.
        assertError(upload(DEV_ALPHA, bugId, "big.png", "image/png", oversized), 400, "FILE_REJECTED");

        perform(cookieFor(DEV_ALPHA), get("/api/projects/{p}/bugs/{b}/attachments", ALPHA, bugId))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void attachmentsAreArchivedNotDeletedAndOnlyByTheUploaderOrALead() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("archive-file"), "LOW"));
        UUID first = id(body(upload(DEV_ALPHA, bugId, "one.png", "image/png", pngBytes()).andExpect(status().isCreated())));
        UUID second = id(body(upload(DEV_ALPHA, bugId, "two.png", "image/png", pngBytes()).andExpect(status().isCreated())));
        String path = "/api/projects/{p}/bugs/{b}/attachments/{a}";

        // A reporter who did not upload the file may not remove it.
        assertError(perform(cookieFor(REPORTER_ALPHA), delete(path, ALPHA, bugId, first)), 403, "ACCESS_DENIED");
        // The uploader and a project lead may.
        perform(cookieFor(DEV_ALPHA), delete(path, ALPHA, bugId, first)).andExpect(status().isNoContent());
        perform(cookieFor(LEAD_ALPHA), delete(path, ALPHA, bugId, second)).andExpect(status().isNoContent());

        perform(cookieFor(DEV_ALPHA), get("/api/projects/{p}/bugs/{b}/attachments", ALPHA, bugId))
                .andExpect(jsonPath("$.length()").value(0));
        assertError(perform(cookieFor(DEV_ALPHA), get(path + "/content", ALPHA, bugId, first)), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from attachments where bug_id = ? and archived", Long.class, bugId))
                .isEqualTo(2);
    }

    // ------------------------------------------------------------ project administration

    @Test
    void administratorsRunTheProjectLifecycleAndTheLastLeadIsProtected() throws Exception {
        String key = randomProjectKey();
        Cookie admin = cookieFor(ADMIN);
        Cookie lead = cookieFor(LEAD_ALPHA);

        JsonNode project = body(perform(admin, post("/api/projects").contentType(JSON)
                .content(json("projectKey", key, "name", "Integration Test Project", "description", "Created by test")))
                .andExpect(status().isCreated()));
        UUID projectId = id(project);
        assertThat(project.get("myRole").isNull()).isTrue();

        assertError(perform(admin, post("/api/projects").contentType(JSON)
                .content(json("projectKey", key, "name", "Duplicate"))), 409, "CONFLICT");
        assertError(perform(admin, post("/api/projects").contentType(JSON)
                .content(json("projectKey", "bad key", "name", "Invalid"))), 400, "VALIDATION_FAILED");

        // Not a member yet.
        assertError(perform(lead, get("/api/projects/{p}", projectId)), 403, "ACCESS_DENIED");

        perform(admin, put("/api/projects/{p}/members", projectId).contentType(JSON)
                .content(json("email", LEAD_ALPHA, "role", "PROJECT_LEAD")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PROJECT_LEAD"));
        perform(admin, put("/api/projects/{p}/members", projectId).contentType(JSON)
                .content(json("email", DEV_ALPHA, "role", "DEVELOPER"))).andExpect(status().isOk());
        assertError(perform(admin, put("/api/projects/{p}/members", projectId).contentType(JSON)
                .content(json("email", "ghost@enterprise-demo.test", "role", "DEVELOPER"))), 404, "NOT_FOUND");
        perform(lead, get("/api/projects/{p}/members", projectId)).andExpect(jsonPath("$.length()").value(2));

        // The only lead cannot demote or remove themselves.
        assertError(perform(lead, put("/api/projects/{p}/members", projectId).contentType(JSON)
                .content(json("email", LEAD_ALPHA, "role", "DEVELOPER"))), 409, "CONFLICT");
        assertError(perform(lead, delete("/api/projects/{p}/members/{u}", projectId, LEAD_ALPHA_ID)), 409, "CONFLICT");
        perform(lead, delete("/api/projects/{p}/members/{u}", projectId, DEV_ALPHA_ID)).andExpect(status().isNoContent());
        perform(lead, get("/api/projects/{p}/members", projectId)).andExpect(jsonPath("$.length()").value(1));

        perform(lead, post(BUGS, projectId).contentType(JSON).content(json("title", "First bug", "priority", "LOW")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.key").value(key + "-1"));

        // Archiving makes the project read-only and removes it from listings.
        perform(admin, delete("/api/projects/{p}", projectId)).andExpect(status().isNoContent());
        assertError(perform(lead, post(BUGS, projectId).contentType(JSON)
                .content(json("title", "Too late", "priority", "LOW"))), 409, "CONFLICT");
        JsonNode listing = body(perform(admin, get("/api/projects")).andExpect(status().isOk()));
        listing.forEach(entry -> assertThat(entry.get("projectKey").asText()).isNotEqualTo(key));
    }

    @Test
    void theDashboardExposesAllStatusesForTheCallersProjects() throws Exception {
        JsonNode dashboard = body(perform(cookieFor(DEV_ALPHA), get("/api/dashboard")).andExpect(status().isOk()));
        assertThat(texts(objectMapper.valueToTree(dashboard.get("bugsByStatus").fieldNames().hasNext()
                ? iterate(dashboard.get("bugsByStatus").fieldNames()) : List.of())))
                .containsExactly("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "REOPENED");
        assertThat(dashboard.get("accessibleProjects").asLong()).isPositive();

        JsonNode admin = body(perform(cookieFor(ADMIN), get("/api/dashboard")).andExpect(status().isOk()));
        assertThat(admin.get("accessibleProjects").asLong()).isGreaterThanOrEqualTo(2);
    }

    private static List<String> iterate(java.util.Iterator<String> names) {
        List<String> result = new ArrayList<>();
        names.forEachRemaining(result::add);
        return result;
    }
}
