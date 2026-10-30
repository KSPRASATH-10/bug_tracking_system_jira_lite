package com.enterprise.bts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SQL injection, stored XSS, mass assignment, header injection, path traversal and error-leakage tests.
 * Note on titles: bug titles are PLAIN TEXT. The server strips control and bidi characters and
 * enforces length, and the SPA only ever renders them as React text nodes (never innerHTML).
 * Markdown fields (descriptions, comments) are additionally neutralised server-side.
 */
class InjectionDefenseIntegrationTest extends AbstractIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String BUGS = "/api/projects/{p}/bugs";

    // ------------------------------------------------------------ SQL injection

    @ParameterizedTest(name = "search payload [{0}] is inert")
    @ValueSource(strings = {
            "' OR '1'='1",
            "'; DROP TABLE bugs; --",
            "\" OR 1=1 --",
            "1; SELECT pg_sleep(5)",
            "%",
            "_",
            "\\",
            "' UNION SELECT password_hash FROM users --"})
    void searchInputIsTreatedAsLiteralTextNeverAsSql(String payload) throws Exception {
        JsonNode page = body(perform(cookieFor(DEV_ALPHA), get(BUGS, ALPHA).param("search", payload))
                .andExpect(status().isOk()));

        // No title contains these literal strings, so a working injection (or an unescaped % / _) would return rows.
        assertThat(page.get("totalElements").asLong()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bugs", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isGreaterThanOrEqualTo(6);
    }

    @Test
    void sortAndDirectionParametersCannotCarryInjection() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        assertError(perform(dev, get(BUGS, ALPHA).param("sort", "title desc; drop table bugs")), 400, "BAD_REQUEST");
        assertError(perform(dev, get(BUGS, ALPHA).param("sort", "(select 1)")), 400, "BAD_REQUEST");
        assertError(perform(dev, get(BUGS, ALPHA).param("direction", "asc;--")), 400, "BAD_REQUEST");
    }

    @Test
    void sqlFragmentsInPathAndQueryIdentifiersAreRejectedAsInvalidParameters() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        assertError(perform(dev, get(BUGS, "1' OR '1'='1")), 400, "INVALID_PARAMETER");
mockMvc.perform(get(BUGS + "/{b}", ALPHA, "1; DROP TABLE bugs").cookie(dev))
        .andExpect(status().isBadRequest());
assertError(perform(dev, get(BUGS, ALPHA).param("assigneeId", "' OR 1=1")), 400, "INVALID_PARAMETER");
    }

    @Test
    void loginIsNotInjectable() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json("email", "admin@enterprise-demo.test' OR '1'='1", "password", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertError(mockMvc.perform(post("/api/auth/login").contentType(JSON)
                .content(json("email", ADMIN, "password", "' OR '1'='1' --"))), 401, "INVALID_CREDENTIALS");
    }

    // ------------------------------------------------------------ stored XSS

    @Test
    void markdownFieldsAreNeutralisedBeforeStorageAndNeverEchoedAsLiveHtml() throws Exception {
        String hostile = "<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n"
                + "[click](javascript:alert(1))\n```\n<b>code block is kept</b>\n```";

        MvcResult created = perform(cookieFor(DEV_ALPHA), post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", unique("xss"), "description", hostile, "priority", "LOW")))
                .andExpect(status().isCreated()).andReturn();
        String raw = created.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode bug = objectMapper.readTree(raw);

        String description = bug.get("description").asText();
        assertThat(description).doesNotContain("<script").doesNotContain("<img")
                .doesNotContain("javascript:").contains("&lt;script>").contains("(#blocked)")
                .contains("<b>code block is kept</b>");

        // Same in the database: raw HTML never reaches storage.
        String stored = jdbc.queryForObject("select description from bugs where id = ?", String.class, id(bug));
        assertThat(stored).isEqualTo(description);
    }

    @Test
    void plainTextTitlesLoseControlCharactersButOtherwiseRoundTripAsText() throws Exception {
        JsonNode bug = body(perform(cookieFor(DEV_ALPHA), post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", "Hello\u0000 \u202EWorld   <b>x</b>", "priority", "LOW")))
                .andExpect(status().isCreated()));
        assertThat(bug.get("title").asText()).isEqualTo("Hello World <b>x</b>");

        // A title that is nothing but invisible characters is rejected rather than stored as an empty string.
        assertError(perform(cookieFor(DEV_ALPHA), post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", "\u0000\u202E", "priority", "LOW"))), 400, "BAD_REQUEST");
    }

    // ------------------------------------------------------------ input validation and limits

    @Test
    void bugInputConstraintsMatchTheDocumentedLimits() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);

        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON).content(json("title", "   ", "priority", "LOW"))),
                400, "VALIDATION_FAILED");
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", "t".repeat(201), "priority", "LOW"))), 400, "VALIDATION_FAILED");
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", "ok", "priority", "URGENT"))), 400, "MALFORMED_REQUEST");
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON).content(json("title", "ok"))),
                400, "VALIDATION_FAILED");
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", "ok", "description", "d".repeat(20001), "priority", "LOW"))),
                400, "VALIDATION_FAILED");

        // Exactly at the limit is fine...
        perform(dev, post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", unique("limit"), "description", "d".repeat(20000), "priority", "LOW")))
                .andExpect(status().isCreated());
        // ...but input that grows beyond the limit once '<' becomes "&lt;" is refused cleanly (never a DB error).
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON)
                .content(json("title", unique("grow"), "description", "<a".repeat(10000), "priority", "LOW"))),
                400, "BAD_REQUEST");
    }

    @Test
    void unknownPropertiesAreRejectedSoWorkflowFieldsCannotBeMassAssigned() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        assertError(perform(dev, post(BUGS, ALPHA).contentType(JSON).content(
                "{\"title\":\"x\",\"priority\":\"LOW\",\"status\":\"CLOSED\",\"resolution\":\"FIXED\","
                        + "\"reporterId\":\"" + ADMIN_ID + "\",\"version\":99}")), 400, "MALFORMED_REQUEST");

        JsonNode bug = createBug(DEV_ALPHA, ALPHA, unique("mass"), "LOW");
        assertError(perform(dev, patch(BUGS + "/{b}", ALPHA, id(bug)).contentType(JSON)
                .content("{\"version\":0,\"status\":\"CLOSED\",\"assigneeId\":\"" + DEV_ALPHA_ID + "\"}")),
                400, "MALFORMED_REQUEST");
        perform(dev, get(BUGS + "/{b}", ALPHA, id(bug))).andExpect(jsonPath("$.status").value("OPEN"));
    }

    // ------------------------------------------------------------ headers, traversal, error hygiene

    @Test
    void correlationIdsAreEchoedWhenSafeAndReplacedWhenHostile() throws Exception {
        MockHttpServletResponse echoed = mockMvc.perform(get("/api/auth/me")
                        .header("X-Correlation-Id", "test-correlation-1234"))
                .andReturn().getResponse();
        assertThat(echoed.getHeader("X-Correlation-Id")).isEqualTo("test-correlation-1234");
        assertThat(objectMapper.readTree(echoed.getContentAsString()).get("correlationId").asText())
                .isEqualTo("test-correlation-1234");

        for (String hostile : List.of("bad id <script>alert(1)</script>", "x", "a".repeat(200), "id;with;semicolons")) {
            String replaced = mockMvc.perform(get("/api/auth/me").header("X-Correlation-Id", hostile))
                    .andReturn().getResponse().getHeader("X-Correlation-Id");
            assertThat(replaced).isNotEqualTo(hostile)
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    @Test
    void uploadFilenamesCannotTraverseDirectoriesOrInjectHeaders() throws Exception {
        UUID bugId = id(createBug(DEV_ALPHA, ALPHA, unique("traversal"), "LOW"));
        String path = "/api/projects/{p}/bugs/{b}/attachments";

        JsonNode traversal = body(perform(cookieFor(DEV_ALPHA), multipart(path, ALPHA, bugId)
                .file(new MockMultipartFile("file", "../../../etc/passwd.png", "image/png", pngBytes())))
                .andExpect(status().isCreated()));
        assertThat(traversal.get("filename").asText()).isEqualTo("passwd.png");

        JsonNode crlf = body(perform(cookieFor(DEV_ALPHA), multipart(path, ALPHA, bugId)
                .file(new MockMultipartFile("file", "a\"b\r\nX-Evil: 1.png", "image/png", pngBytes())))
                .andExpect(status().isCreated()));
        MockHttpServletResponse download = perform(cookieFor(DEV_ALPHA),
                get(path + "/{a}/content", ALPHA, bugId, id(crlf))).andExpect(status().isOk()).andReturn().getResponse();
        String disposition = download.getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;").doesNotContain("\r").doesNotContain("\n");
        assertThat(download.getHeader("X-Evil")).isNull();
    }

    @Test
    void errorResponsesNeverLeakInternals() throws Exception {
        Cookie dev = cookieFor(DEV_ALPHA);
        List<MvcResult> failures = List.of(
                perform(dev, post(BUGS, ALPHA).contentType(JSON).content("{")).andReturn(),
                perform(dev, get(BUGS + "/{b}", ALPHA, UUID.randomUUID())).andReturn(),
                perform(dev, get("/api/does-not-exist")).andReturn(),
                perform(dev, get(BUGS, "not-a-uuid")).andReturn(),
                perform(dev, get(BUGS, ALPHA).param("sort", "nope")).andReturn(),
                perform(null, get(BUGS, ALPHA)).andReturn(),
                perform(cookieFor(DEV_BETA), get(BUGS, ALPHA)).andReturn());

        for (MvcResult failure : failures) {
            MockHttpServletResponse response = failure.getResponse();
            String text = response.getContentAsString(StandardCharsets.UTF_8);
            assertThat(response.getStatus()).isBetween(400, 499);
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(text.toLowerCase())
                    .doesNotContain("exception").doesNotContain("org.springframework").doesNotContain("com.bny")
                    .doesNotContain("jackson").doesNotContain("hibernate").doesNotContain("stacktrace")
                    .doesNotContain("whitelabel").doesNotContain("select ").doesNotContain("\tat ");
            JsonNode error = objectMapper.readTree(text);
            assertThat(error.get("correlationId").asText()).isNotBlank();
            assertThat(error.get("code").asText()).isNotBlank();
        }
        assertError(perform(dev, get("/api/does-not-exist")), 404, "NOT_FOUND");
    }
}
