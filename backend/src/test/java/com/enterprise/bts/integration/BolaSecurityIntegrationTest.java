package com.enterprise.bts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * BOLA / IDOR and privilege-escalation negative tests. Every probe carries a syntactically VALID body, because
 * Spring binds the request body before method security runs, and a malformed body would otherwise
 * mask the authorisation outcome with a 400.
 */
class BolaSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    record Probe(String name, Supplier<MockHttpServletRequestBuilder> request) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** One request for every project-scoped endpoint in the API. */
    static Stream<Probe> probesFor(UUID p, UUID b) {
        String base = "/api/projects/{p}/bugs";
        String bug = base + "/{b}";
        return Stream.of(
                new Probe("GET project", () -> get("/api/projects/{p}", p)),
                new Probe("GET members", () -> get("/api/projects/{p}/members", p)),
                new Probe("PUT member", () -> put("/api/projects/{p}/members", p).contentType(JSON)
                        .content("{\"email\":\"dev.beta@enterprise-demo.test\",\"role\":\"DEVELOPER\"}")),
                new Probe("DELETE member", () -> delete("/api/projects/{p}/members/{u}", p, UUID.randomUUID())),
                new Probe("DELETE project", () -> delete("/api/projects/{p}", p)),
                new Probe("GET bug list", () -> get(base, p)),
                new Probe("POST bug", () -> post(base, p).contentType(JSON)
                        .content("{\"title\":\"x\",\"priority\":\"LOW\"}")),
                new Probe("GET bug", () -> get(bug, p, b)),
                new Probe("PATCH bug", () -> patch(bug, p, b).contentType(JSON)
                        .content("{\"title\":\"hacked\",\"version\":0}")),
                new Probe("POST transition", () -> post(bug + "/transition", p, b).contentType(JSON)
                        .content("{\"targetStatus\":\"IN_PROGRESS\",\"version\":0}")),
                new Probe("PUT assignee", () -> put(bug + "/assignee", p, b).contentType(JSON)
                        .content("{\"assigneeId\":null,\"version\":0}")),
                new Probe("DELETE bug", () -> delete(bug, p, b)),
                new Probe("GET timeline", () -> get(bug + "/timeline", p, b)),
                new Probe("GET comments", () -> get(bug + "/comments", p, b)),
                new Probe("POST comment", () -> post(bug + "/comments", p, b).contentType(JSON)
                        .content("{\"body\":\"hello\"}")),
                new Probe("GET attachments", () -> get(bug + "/attachments", p, b)),
                new Probe("POST attachment", () -> multipart(bug + "/attachments", p, b)
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8)))),
                new Probe("GET attachment content", () -> get(bug + "/attachments/{a}/content", p, b, UUID.randomUUID())),
                new Probe("DELETE attachment", () -> delete(bug + "/attachments/{a}", p, b, UUID.randomUUID())));
    }

    static Stream<Probe> alphaProbes() {
        return probesFor(ALPHA, ALPHA_BUG_OPEN);
    }

    static Stream<Probe> betaProbes() {
        return probesFor(BETA, BETA_BUG_OPEN);
    }

    // ------------------------------------------------------------ cross-project: 403 on every endpoint

    private void assertForbiddenWithoutLeak(Cookie cookie, Probe probe) throws Exception {
        MvcResult result = perform(cookie, probe.request().get())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andReturn();
        String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain("Settlement").doesNotContain("Treasury")
                .doesNotContain("Login button").doesNotContain("Liquidity chart");
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank()
                .isEqualTo(objectMapper.readTree(responseBody).get("correlationId").asText());
    }

    @ParameterizedTest(name = "dev of BETA is forbidden from ALPHA: {0}")
    @MethodSource("alphaProbes")
    void developerOfProjectBetaCannotTouchProjectAlpha(Probe probe) throws Exception {
        assertForbiddenWithoutLeak(cookieFor(DEV_BETA), probe);
    }

    @ParameterizedTest(name = "lead of BETA is forbidden from ALPHA: {0}")
    @MethodSource("alphaProbes")
    void leadershipDoesNotCrossProjectBoundaries(Probe probe) throws Exception {
        assertForbiddenWithoutLeak(cookieFor(LEAD_BETA), probe);
    }

    @ParameterizedTest(name = "dev of ALPHA is forbidden from BETA: {0}")
    @MethodSource("betaProbes")
    void developerOfProjectAlphaCannotTouchProjectBeta(Probe probe) throws Exception {
        assertForbiddenWithoutLeak(cookieFor(DEV_ALPHA), probe);
    }

    @ParameterizedTest(name = "reporter of ALPHA is forbidden from BETA: {0}")
    @MethodSource("betaProbes")
    void reporterOfProjectAlphaCannotTouchProjectBeta(Probe probe) throws Exception {
        assertForbiddenWithoutLeak(cookieFor(REPORTER_ALPHA), probe);
    }

    @ParameterizedTest(name = "anonymous gets 401: {0}")
    @MethodSource("alphaProbes")
    void unauthenticatedCallersAreRejectedEverywhere(Probe probe) throws Exception {
        assertError(perform(null, probe.request().get()), 401, "UNAUTHENTICATED");
    }

    // ------------------------------------------------------------ bug id from another project

    @Test
    void aBugIdFromAnotherProjectIsNotFoundUnderTheCallersOwnProject() throws Exception {
        Cookie devBeta = cookieFor(DEV_BETA);
        Cookie leadBeta = cookieFor(LEAD_BETA);
        String bug = "/api/projects/{p}/bugs/{b}";

        // devBeta passes the project gate for BETA, but the ALPHA bug is invisible inside BETA.
        assertError(perform(devBeta, get(bug, BETA, ALPHA_BUG_OPEN)), 404, "NOT_FOUND");
        assertError(perform(devBeta, get(bug + "/timeline", BETA, ALPHA_BUG_OPEN)), 404, "NOT_FOUND");
        assertError(perform(devBeta, get(bug + "/comments", BETA, ALPHA_BUG_OPEN)), 404, "NOT_FOUND");
        assertError(perform(devBeta, get(bug + "/attachments", BETA, ALPHA_BUG_OPEN)), 404, "NOT_FOUND");
        assertError(perform(devBeta, post(bug + "/comments", BETA, ALPHA_BUG_OPEN).contentType(JSON)
                .content("{\"body\":\"hi\"}")), 404, "NOT_FOUND");
        assertError(perform(devBeta, post(bug + "/transition", BETA, ALPHA_BUG_OPEN).contentType(JSON)
                .content("{\"targetStatus\":\"IN_PROGRESS\",\"version\":0}")), 404, "NOT_FOUND");
        assertError(perform(leadBeta, patch(bug, BETA, ALPHA_BUG_OPEN).contentType(JSON)
                .content("{\"title\":\"hijacked\",\"version\":0}")), 404, "NOT_FOUND");
        assertError(perform(leadBeta, put(bug + "/assignee", BETA, ALPHA_BUG_OPEN).contentType(JSON)
                .content("{\"assigneeId\":null,\"version\":0}")), 404, "NOT_FOUND");
        assertError(perform(leadBeta, delete(bug, BETA, ALPHA_BUG_OPEN)), 404, "NOT_FOUND");

        // ...and the ALPHA bug is untouched.
        perform(cookieFor(DEV_ALPHA), get(bug, ALPHA, ALPHA_BUG_OPEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Login button unresponsive on Safari 17"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void attachmentIdsAreAlsoScopedToTheirBug() throws Exception {
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("scope"), "LOW");
        JsonNode attachment = body(perform(cookieFor(DEV_ALPHA),
                multipart("/api/projects/{p}/bugs/{b}/attachments", ALPHA, id(bug))
                        .file(new MockMultipartFile("file", "shot.png", "image/png", pngBytes())))
                .andExpect(status().isCreated()));
        UUID attachmentId = id(attachment);

        // Right project, wrong bug: 404, not a cross-bug download.
        assertError(perform(cookieFor(DEV_ALPHA), get("/api/projects/{p}/bugs/{b}/attachments/{a}/content",
                ALPHA, ALPHA_BUG_OPEN, attachmentId)), 404, "NOT_FOUND");
        // Wrong project entirely: 403.
        assertError(perform(cookieFor(DEV_BETA), get("/api/projects/{p}/bugs/{b}/attachments/{a}/content",
                ALPHA, id(bug), attachmentId)), 403, "ACCESS_DENIED");
    }

    // ------------------------------------------------------------ administrators, enumeration, scoping

    @Test
    void administratorsCanReadEveryProject() throws Exception {
        Cookie admin = cookieFor(ADMIN);
        perform(admin, get("/api/projects/{p}/bugs", ALPHA)).andExpect(status().isOk());
        perform(admin, get("/api/projects/{p}/bugs", BETA)).andExpect(status().isOk());
        perform(admin, get("/api/projects/{p}/bugs/{b}", BETA, BETA_BUG_OPEN)).andExpect(status().isOk());
        perform(admin, get("/api/projects/{p}/bugs/{b}", ALPHA, ALPHA_BUG_OPEN)).andExpect(status().isOk());
    }

    @Test
    void projectIdsCannotBeEnumerated() throws Exception {
        UUID doesNotExist = UUID.randomUUID();
        // A non-member gets the same 403 whether or not the project exists...
        assertError(perform(cookieFor(DEV_BETA), get("/api/projects/{p}/bugs", doesNotExist)), 403, "ACCESS_DENIED");
        assertError(perform(cookieFor(DEV_BETA), get("/api/projects/{p}/bugs", ALPHA)), 403, "ACCESS_DENIED");
        // ...only an administrator can learn that a project does not exist.
        assertError(perform(cookieFor(ADMIN), get("/api/projects/{p}/bugs", doesNotExist)), 404, "NOT_FOUND");
    }

    @Test
    void projectListingAndDashboardAreScopedToMembership() throws Exception {
        assertThat(projectKeys(cookieFor(DEV_BETA))).containsExactly("BETA");
        assertThat(projectKeys(cookieFor(REPORTER_ALPHA))).containsExactly("ALPHA");
        assertThat(projectKeys(cookieFor(ADMIN))).contains("ALPHA", "BETA");

        perform(cookieFor(DEV_BETA), get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessibleProjects").value(1));
        JsonNode dashboard = body(perform(cookieFor(DEV_BETA), get("/api/dashboard")));
        dashboard.get("myOpenBugs").forEach(bug ->
                assertThat(bug.get("projectId").asText()).isEqualTo(BETA.toString()));
    }

    private List<String> projectKeys(Cookie cookie) throws Exception {
        JsonNode projects = body(perform(cookie, get("/api/projects")).andExpect(status().isOk()));
        List<String> keys = new ArrayList<>();
        projects.forEach(project -> keys.add(project.get("projectKey").asText()));
        return keys;
    }

    // ------------------------------------------------------------ project-scoped RBAC

    @Test
    void reportersCanReportAndCommentButCannotTransitionAssignOrAdminister() throws Exception {
        Cookie reporter = cookieFor(REPORTER_ALPHA);
        JsonNode bug = createBug(REPORTER_ALPHA, ALPHA, unique("rbac-reporter"), "LOW");
        UUID bugId = id(bug);

        assertError(transitionRaw(reporter, ALPHA, bugId, "IN_PROGRESS", null, 0L), 403, "ACCESS_DENIED");
        assertError(perform(reporter, put("/api/projects/{p}/bugs/{b}/assignee", ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", DEV_ALPHA_ID, "version", 0))), 403, "ACCESS_DENIED");
        assertError(perform(reporter, delete("/api/projects/{p}/bugs/{b}", ALPHA, bugId)), 403, "ACCESS_DENIED");
        assertError(perform(reporter, put("/api/projects/{p}/members", ALPHA).contentType(JSON)
                .content(json("email", DEV_BETA, "role", "DEVELOPER"))), 403, "ACCESS_DENIED");
        assertError(perform(reporter, delete("/api/projects/{p}", ALPHA)), 403, "ACCESS_DENIED");

        perform(reporter, post("/api/projects/{p}/bugs/{b}/comments", ALPHA, bugId).contentType(JSON)
                .content("{\"body\":\"Adding context\"}")).andExpect(status().isCreated());
        // May edit a bug they reported, but not someone else's.
        perform(reporter, patch("/api/projects/{p}/bugs/{b}", ALPHA, bugId).contentType(JSON)
                .content(json("title", unique("renamed"), "version", bug.get("version").asLong())))
                .andExpect(status().isOk());
        assertError(perform(reporter, patch("/api/projects/{p}/bugs/{b}", ALPHA, ALPHA_BUG_RESOLVED).contentType(JSON)
                .content("{\"title\":\"not mine\",\"version\":0}")), 403, "ACCESS_DENIED");
    }

    @Test
    void developersCanTransitionButCannotAssignArchiveOrManageMembers() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("rbac-dev"), "MEDIUM");
        UUID bugId = id(bug);

        assertError(perform(dev, put("/api/projects/{p}/bugs/{b}/assignee", ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", DEV_ALPHA_ID, "version", 0))), 403, "ACCESS_DENIED");
        assertError(perform(dev, delete("/api/projects/{p}/bugs/{b}", ALPHA, bugId)), 403, "ACCESS_DENIED");
        assertError(perform(dev, put("/api/projects/{p}/members", ALPHA).contentType(JSON)
                .content(json("email", DEV_BETA, "role", "DEVELOPER"))), 403, "ACCESS_DENIED");
        assertError(perform(dev, delete("/api/projects/{p}/members/{u}", ALPHA, REPORTER_ALPHA_ID)),
                403, "ACCESS_DENIED");
        assertError(perform(dev, delete("/api/projects/{p}", ALPHA)), 403, "ACCESS_DENIED");

        transitionRaw(dev, ALPHA, bugId, "IN_PROGRESS", null, bug.get("version").asLong())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void leadsAssignOnlyDevelopersOrLeadsOfTheSameProject() throws Exception {
        Cookie lead = cookieFor(LEAD_ALPHA);
        JsonNode bug = createBug(REPORTER_ALPHA, ALPHA, unique("rbac-assign"), "LOW");
        String path = "/api/projects/{p}/bugs/{b}/assignee";
        UUID bugId = id(bug);
        long version = bug.get("version").asLong();

        // A developer of ANOTHER project, a reporter-role member and an unknown user are all refused.
        assertError(perform(lead, put(path, ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", DEV_BETA_ID, "version", version))), 400, "BAD_REQUEST");
        assertError(perform(lead, put(path, ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", REPORTER_ALPHA_ID, "version", version))), 400, "BAD_REQUEST");
        assertError(perform(lead, put(path, ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", UUID.randomUUID(), "version", version))), 400, "BAD_REQUEST");

        JsonNode assigned = body(perform(lead, put(path, ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", DEV_ALPHA_ID, "version", version))).andExpect(status().isOk()));
        assertThat(assigned.get("assignee").get("id").asText()).isEqualTo(DEV_ALPHA_ID.toString());

        JsonNode unassigned = body(perform(lead, put(path, ALPHA, bugId).contentType(JSON)
                .content(json("assigneeId", null, "version", assigned.get("version").asLong())))
                .andExpect(status().isOk()));
        assertThat(unassigned.get("assignee").isNull()).isTrue();
    }

    @Test
    void onlyAdministratorsCanCreateProjects() throws Exception {
        String payload = json("projectKey", "HACK", "name", "Rogue project");
        for (String email : List.of(LEAD_ALPHA, DEV_ALPHA, REPORTER_ALPHA, LEAD_BETA)) {
            assertError(perform(cookieFor(email), post("/api/projects").contentType(JSON).content(payload)),
                    403, "ACCESS_DENIED");
        }
        assertThat(jdbc.queryForObject("select count(*) from projects where project_key = 'HACK'", Long.class)).isZero();
    }

    // ------------------------------------------------------------ audit trail of denials

    @Test
    void everyDenialIsWrittenToTheAppendOnlyAuditLog() throws Exception {
        Long before = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ACCESS_DENIED' and actor_id = ?", Long.class, DEV_BETA_ID);

        perform(cookieFor(DEV_BETA), get("/api/projects/{p}/bugs", ALPHA)).andExpect(status().isForbidden());

        Long after = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ACCESS_DENIED' and actor_id = ?", Long.class, DEV_BETA_ID);
        assertThat(after).isEqualTo(before + 1);
        String details = jdbc.queryForObject(
                "select details::text from audit_log where action = 'ACCESS_DENIED' and actor_id = ? "
                        + "order by id desc limit 1", String.class, DEV_BETA_ID);
        assertThat(details).contains("/api/projects/" + ALPHA + "/bugs");
    }
}
