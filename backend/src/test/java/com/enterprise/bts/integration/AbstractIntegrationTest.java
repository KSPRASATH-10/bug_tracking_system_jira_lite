package com.enterprise.bts.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the complete application (security filter chain, method security, Flyway V1+V2, JPA) against a
 * real PostgreSQL 16 started by Testcontainers. One container and one Spring context are shared by
 * every integration test class. Tests never mutate the seeded bugs; they create their own, so test
 * order does not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // ---- seeded accounts (V2__seed_test_data.sql); all share the documented password
    protected static final String PASSWORD = "Enterprise#Demo2026!";
    protected static final String ADMIN = "admin@enterprise-demo.test";
    protected static final String LEAD_ALPHA = "lead.alpha@enterprise-demo.test";
    protected static final String DEV_ALPHA = "dev.alpha@enterprise-demo.test";
    protected static final String REPORTER_ALPHA = "reporter.alpha@enterprise-demo.test";
    protected static final String LEAD_BETA = "lead.beta@enterprise-demo.test";
    protected static final String DEV_BETA = "dev.beta@enterprise-demo.test";

    protected static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    protected static final UUID LEAD_ALPHA_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    protected static final UUID DEV_ALPHA_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    protected static final UUID REPORTER_ALPHA_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    protected static final UUID LEAD_BETA_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");
    protected static final UUID DEV_BETA_ID = UUID.fromString("00000000-0000-4000-8000-000000000006");

    // ---- seeded projects and bugs
    protected static final UUID ALPHA = UUID.fromString("20000000-0000-4000-8000-0000000000a1");
    protected static final UUID BETA = UUID.fromString("20000000-0000-4000-8000-0000000000b1");
    /** ALPHA-1: OPEN, reported by the ALPHA reporter. Never mutated by any test. */
    protected static final UUID ALPHA_BUG_OPEN = UUID.fromString("30000000-0000-4000-8000-0000000000a1");
    /** ALPHA-3: RESOLVED, reported by the ALPHA lead. Never mutated by any test. */
    protected static final UUID ALPHA_BUG_RESOLVED = UUID.fromString("30000000-0000-4000-8000-0000000000a3");
    /** BETA-1: OPEN. Never mutated by any test. */
    protected static final UUID BETA_BUG_OPEN = UUID.fromString("30000000-0000-4000-8000-0000000000b1");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bts")
            .withUsername("bts_app")
            .withPassword("bts_test_pw");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;

    private record CachedCookie(Cookie cookie, Instant createdAt) {
    }

    /** BCrypt(12) is deliberately slow, so sessions are cached (and refreshed well before the 5-minute test TTL). */
    private static final Map<String, CachedCookie> COOKIES = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ authentication helpers

    protected Cookie cookieFor(String email) throws Exception {
        CachedCookie cached = COOKIES.get(email);
        if (cached == null || cached.createdAt().isBefore(Instant.now().minusSeconds(150))) {
            cached = new CachedCookie(loginAndGetCookie(email, PASSWORD), Instant.now());
            COOKIES.put(email, cached);
        }
        return cached.cookie();
    }

    protected Cookie loginAndGetCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookieFrom(result.getResponse());
    }

    protected static Cookie cookieFrom(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        if (header == null) {
            throw new AssertionError("No Set-Cookie header on the response");
        }
        String pair = header.split(";", 2)[0];
        int equals = pair.indexOf('=');
        return new Cookie(pair.substring(0, equals), pair.substring(equals + 1));
    }

    // ------------------------------------------------------------------ request / JSON helpers

    protected ResultActions perform(Cookie cookie, MockHttpServletRequestBuilder request) throws Exception {
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request);
    }

    protected String json(Object... keyValues) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected static void assertError(ResultActions actions, int expectedStatus, String expectedCode) throws Exception {
        actions.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    protected static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    protected static UUID projectIdOf(JsonNode bug) {
        return UUID.fromString(bug.get("projectId").asText());
    }

    protected static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(element -> values.add(element.asText()));
        return values;
    }

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String randomProjectKey() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder key = new StringBuilder("T");
        for (int i = 0; i < 7; i++) {
            key.append(alphabet.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return key.toString();
    }

    // ------------------------------------------------------------------ domain helpers

    protected JsonNode createBug(String asEmail, UUID projectId, String title, String priority) throws Exception {
        return body(perform(cookieFor(asEmail), post("/api/projects/{p}/bugs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("title", title, "description", "Created by an integration test", "priority", priority)))
                .andExpect(status().isCreated()));
    }

    protected ResultActions transitionRaw(Cookie cookie, UUID projectId, UUID bugId, String target,
                                          String resolution, Long version) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetStatus", target);
        if (resolution != null) {
            payload.put("resolution", resolution);
        }
        if (version != null) {
            payload.put("version", version);
        }
        return perform(cookie, post("/api/projects/{p}/bugs/{b}/transition", projectId, bugId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    /** Expects HTTP 200 and returns the updated bug. */
    protected JsonNode transition(String asEmail, JsonNode bug, String target, String resolution) throws Exception {
        return body(transitionRaw(cookieFor(asEmail), projectIdOf(bug), id(bug), target, resolution,
                bug.get("version").asLong()).andExpect(status().isOk()));
    }

    protected long auditCount(String action, UUID entityId) {
        Long count = jdbc.queryForObject(
                "select count(*) from audit_log where action = ? and entity_id = ?", Long.class, action, entityId);
        return count == null ? 0 : count;
    }

    protected static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    /** A real PNG header + IHDR chunk: detected as image/png by Apache Tika. */
    protected static byte[] pngBytes() {
        return bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, 0x90, 0x77, 0x53, 0xDE);
    }
}
