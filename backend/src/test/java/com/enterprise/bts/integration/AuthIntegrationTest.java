package com.enterprise.bts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.bts.config.BtsProperties;
import com.enterprise.bts.security.JwtService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BtsProperties properties;
    @Autowired
    private JwtService jwtService;

    private String credentials(String email, String password) throws Exception {
        return json("email", email, "password", password);
    }

    private Cookie authCookie(String token) {
        return new Cookie(properties.security().cookieName(), token);
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------ login

    @Test
    void loginSetsAHardenedCookieAndNeverReturnsTheTokenInTheBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(DEV_ALPHA, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEV_ALPHA))
                .andExpect(jsonPath("$.systemRole").value("USER"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith("BTS_AUTH=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api")
                .contains("Max-Age=300");

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("jwt")
                .doesNotContainIgnoringCase("password");
    }

    @Test
    void loginNormalisesTheEmailCase() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("Dev.Alpha@ENTERPRISE-DEMO.TEST", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEV_ALPHA));
    }

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(DEV_ALPHA, "definitely-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        MvcResult unknownUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody@enterprise-demo.test", "definitely-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(objectMapper.readTree(wrongPassword.getResponse().getContentAsString()).get("message"))
                .isEqualTo(objectMapper.readTree(unknownUser.getResponse().getContentAsString()).get("message"));
    }

    @Test
    void failedLoginIsAuditedWithoutLeakingTheAttemptedPassword() throws Exception {
        Long before = jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN_FAILED'", Long.class);
        String attempted = "S3cret-attempt-" + UUID.randomUUID();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(DEV_ALPHA, attempted)))
                .andExpect(status().isUnauthorized());

        Long after = jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN_FAILED'", Long.class);
        assertThat(after).isEqualTo(before + 1);
        String details = jdbc.queryForObject(
                "select details::text from audit_log where action = 'LOGIN_FAILED' order by id desc limit 1",
                String.class);
        assertThat(details).doesNotContain(attempted).doesNotContain(DEV_ALPHA);
    }

    @Test
    void invalidLoginPayloadsAreRejectedWithStructuredErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        // Unknown properties are refused (no mass-assignment style probing).
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"x\",\"admin\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("email=a&password=b"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void seededPasswordsAreBcryptWithCost12() {
        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, ADMIN);
        assertThat(hash).startsWith("$2a$12$");
    }

    // ------------------------------------------------------------ session / token integrity

    @Test
    void meRequiresAValidSession() throws Exception {
        assertError(mockMvc.perform(get("/api/auth/me")), 401, "UNAUTHENTICATED");

        mockMvc.perform(get("/api/auth/me").cookie(cookieFor(DEV_ALPHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEV_ALPHA))
                .andExpect(jsonPath("$.id").value(DEV_ALPHA_ID.toString()));
    }

    @Test
    void aTamperedSignatureIsRejected() throws Exception {
        String[] parts = loginAndGetCookie(DEV_ALPHA, PASSWORD).getValue().split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(String.join(".", parts)))),
                401, "UNAUTHENTICATED");
    }

    @Test
    void editingTheClaimsToImpersonateAnAdministratorIsRejected() throws Exception {
        String[] parts = loginAndGetCookie(DEV_ALPHA, PASSWORD).getValue().split("\\.");
        String payload = "{\"sub\":\"" + ADMIN_ID + "\",\"iss\":\"enterprise-jira-lite\",\"exp\":"
                + (Instant.now().getEpochSecond() + 3600) + "}";
        String forged = parts[0] + "." + base64Url(payload) + "." + parts[2];

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(forged))), 401, "UNAUTHENTICATED");
    }

    @Test
    void anUnsignedAlgNoneTokenIsRejected() throws Exception {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + ADMIN_ID + "\",\"iss\":\"enterprise-jira-lite\",\"exp\":"
                + (Instant.now().getEpochSecond() + 3600) + "}");

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(header + "." + payload + "."))),
                401, "UNAUTHENTICATED");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        BtsProperties other = new BtsProperties(
                new BtsProperties.Security("a-completely-different-secret-key-0123456789", 30, true, "BTS_AUTH",
                        "http://localhost:5173"),
                properties.rateLimit(), properties.storage());
        String forged = new JwtService(other).issueToken(ADMIN_ID);

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(forged))), 401, "UNAUTHENTICATED");
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        JwtService past = new JwtService(properties, Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));
        String expired = past.issueToken(DEV_ALPHA_ID);

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(expired))), 401, "UNAUTHENTICATED");
    }

    @Test
    void aValidTokenForAnUnknownUserIsRejected() throws Exception {
        String token = jwtService.issueToken(UUID.randomUUID());

        assertError(mockMvc.perform(get("/api/auth/me").cookie(authCookie(token))), 401, "UNAUTHENTICATED");
    }

    @Test
    void disablingAnAccountRevokesItsExistingSessionImmediately() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@enterprise-demo.test";
        jdbc.update("insert into users (email, password_hash, full_name) values (?, crypt(?, gen_salt('bf', 12)), ?)",
                email, PASSWORD, "Temporary User");
        Cookie session = loginAndGetCookie(email, PASSWORD);
        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

        jdbc.update("update users set enabled = false where email = ?", email);

        assertError(mockMvc.perform(get("/api/auth/me").cookie(session)), 401, "UNAUTHENTICATED");
        assertError(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD))), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void logoutClearsTheCookieAndIsSafeWhenAlreadySignedOut() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/logout").cookie(cookieFor(DEV_ALPHA)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("BTS_AUTH=").contains("Max-Age=0")
                .contains("HttpOnly").contains("SameSite=Strict");

        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ CSRF / CORS / headers

    @Test
    void stateChangingRequestsFromAForeignOriginAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .contentType(MediaType.APPLICATION_JSON)
        .content(credentials(DEV_ALPHA, PASSWORD)))
        .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(DEV_ALPHA, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightIsAllowedOnlyForTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void apiResponsesCarryHardeningHeaders() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse();
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'")
                .contains("frame-ancestors 'none'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void unlistedPathsAreDeniedWithoutRevealingWhetherTheyExist() throws Exception {
        assertError(mockMvc.perform(get("/actuator/env")), 401, "UNAUTHENTICATED");
        assertError(mockMvc.perform(get("/admin")), 401, "UNAUTHENTICATED");
    }

    // ------------------------------------------------------------ OpenAPI

    @Test
    void openApiDocumentDescribesTheSecuritySchemeAndEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Enterprise Jira Lite API"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.paths['/api/projects/{projectId}/bugs']").exists())
                .andExpect(jsonPath("$.paths['/api/projects/{projectId}/bugs/{bugId}/transition']").exists());
    }
}
