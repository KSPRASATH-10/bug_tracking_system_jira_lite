package com.enterprise.bts.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 definition served at /v3/api-docs, with Swagger UI at /swagger-ui.html.
 * Authentication is the HttpOnly session cookie: call POST /api/auth/login from Swagger UI "Try it out"
 * and the browser stores the cookie and sends it on subsequent calls from the same origin.
 * For a hardened production deployment set springdoc.api-docs.enabled=false.
 */
@Configuration
public class OpenApiConfig {

    static final String COOKIE_SCHEME = "cookieAuth";

    @Bean
    public OpenAPI jiraLiteOpenApi(BtsProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise Jira Lite API")
                        .version("1.0.0")
                        .description("""
                                Enterprise bug tracking API.

                                **Authentication:** `POST /api/auth/login` sets an `HttpOnly; SameSite=Strict` cookie. \
                                No token is ever returned in a response body.

                                **Errors:** every failure uses one JSON envelope containing `status`, `code`, `message`, \
                                `correlationId` (also returned in the `X-Correlation-Id` header) and optional `fieldErrors`.

                                **Concurrency:** bug-changing calls require the `version` last read; a stale version \
                                returns `409 CONCURRENT_MODIFICATION`.

                                **Workflow:** OPEN → IN_PROGRESS → RESOLVED → CLOSED | REOPENED; CLOSED → REOPENED; \
                                REOPENED → IN_PROGRESS. Closing requires a `resolution`. Illegal transitions return `400`."""))
                .components(new Components().addSecuritySchemes(COOKIE_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(properties.security().cookieName())
                        .description("HttpOnly JWT session cookie issued by POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }
}
