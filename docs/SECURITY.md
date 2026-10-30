# Security

This document maps each risk to the control that addresses it, where the control lives, and the test that
exercises it. It also lists what is deliberately not covered. Read "Known limitations" and the production
checklist before any deployment beyond a developer machine.

## 1. Trust boundaries

| Boundary | Threats | Controls |
|---|---|---|
| Browser to API | Credential stuffing, token theft, CSRF, header injection | HttpOnly cookie session, login rate limit, Origin check, single-origin CORS, CSP and `nosniff`, sanitised correlation ids |
| Authentication to authorisation | BOLA/IDOR, privilege escalation | `@PreAuthorize` on every project-scoped route, scoped queries, role re-checks in services |
| Application to database | SQL injection, lost updates, tampering | Bound parameters only, allow-listed sort fields, `@Version`, CHECK constraints, append-only triggers |
| Application to filesystem | Malicious upload, path traversal | Tika magic-byte validation, extension deny-list, UUID storage names, forced download |

## 2. Control map

| Risk | Control | Where | Verified by |
|---|---|---|---|
| Token theft via XSS | JWT only in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api`. No token is ever in a response body or in browser storage | `AuthCookieService`, `AuthController`, `AuthContext.tsx` | `AuthIntegrationTest` |
| Forged or altered tokens | HS256 only, issuer required, expiry enforced, `alg=none` and foreign-key tokens rejected, user re-loaded from the database on every request (a disabled account loses its session immediately) | `JwtService`, `JwtAuthenticationFilter` | `AuthIntegrationTest` |
| Weak credentials at rest | BCrypt cost 12. Unknown users still trigger a dummy hash check, so response time and message do not reveal account existence | `SecurityConfig`, `AuthService` | `AuthIntegrationTest` |
| Credential stuffing | In-memory token bucket per client address on `POST /api/auth/login`: 429 with `Retry-After` | `LoginRateLimitFilter` | `LoginRateLimitFilterTest` |
| CSRF | `SameSite=Strict`, one allowed CORS origin, and any state-changing request with a foreign `Origin` header is refused | `SecurityConfig` | `AuthIntegrationTest` |
| BOLA / IDOR | Membership is read from the database for every call. A non-member gets the same 403 whether or not the project exists. Bugs and attachments are always resolved inside the authorised project or bug, so a foreign id is a 404 | `ProjectSecurity`, `BugRepository.findActiveInProject` | `BolaSecurityIntegrationTest` |
| Privilege escalation | Role matrix enforced in controllers and again in services. Assignees must be developers or leads of the same project | `BugService`, `ProjectService` | `BolaSecurityIntegrationTest` |
| Workflow tampering | One state machine. CLOSED requires a resolution and any other status must not carry one. A database CHECK backs this | `BugStateMachine`, `ck_bugs_resolution_iff_closed` | `BugStateMachineTest`, `BugWorkflowIntegrationTest` |
| Lost updates | Client-supplied `version` compared first, then Hibernate `@Version` on flush. Both produce 409 | `BugService`, `Bug` | `OptimisticLockingIntegrationTest` |
| SQL injection | JPA Criteria and bound parameters, `LIKE` wildcards escaped, sort and direction allow-listed | `BugSpecifications`, `BugService` | `InjectionDefenseIntegrationTest` |
| Stored XSS | Markdown sanitiser neutralises tag openers and script-scheme links before storage. The SPA renders Markdown without raw HTML, with an allow-listed link scheme and no remote images | `MarkdownSanitizer`, `ActivityTimeline.tsx` | `MarkdownSanitizerTest`, `InjectionDefenseIntegrationTest` |
| Mass assignment | Strict binding: unknown JSON properties are rejected | `application.yml` | `InjectionDefenseIntegrationTest` |
| Malicious upload | Max 5 MiB and 25 files per bug, Tika magic bytes must agree with extension and declared MIME, dangerous and double extensions refused, UUID storage names, owner-only file permissions, `Content-Disposition: attachment` plus `nosniff` | `FileValidationService`, `AttachmentService`, `AttachmentController` | `FileValidationServiceTest`, `BugWorkflowIntegrationTest` |
| Information leakage | One error envelope, no stack traces, SQL or class names, with a correlation id for support | `GlobalExceptionHandler` | `InjectionDefenseIntegrationTest` |
| Repudiation / tampering with history | `bug_history`, `bug_comments` and `audit_log` reject UPDATE, DELETE and TRUNCATE by trigger. Core tables reject hard DELETE. Audit rows join the business transaction, so they never describe a rolled-back change | `V1__init_schema.sql`, `AuditService` | `BugWorkflowIntegrationTest` |
| Log forging | Correlation ids are accepted only if they are short safe tokens. Untrusted text is stripped of control characters before logging | `CorrelationIdFilter`, `GlobalExceptionHandler` | `InjectionDefenseIntegrationTest` |

Audit records never contain passwords, tokens or the email typed into a failed login (users sometimes paste a
password into that field).

## 3. Known limitations

These are real gaps. Do not assume otherwise.

- **Single-node rate limiting.** The limiter is in memory and keyed by the TCP peer address. Behind a reverse proxy
  that address is the proxy, which makes the limit effectively global. `X-Forwarded-For` is intentionally not trusted.
  Use a proxy-aware setup or a shared store before putting this behind a load balancer.
- **No server-side session revocation.** Logout clears the cookie. A copied token stays valid until it expires
  (30 minutes by default). Disabling a user, however, takes effect on the next request.
- **No MFA, SSO, password reset or password policy.** Accounts are seeded. Enterprise use needs an identity provider.
- **No malware scanning of uploads.** Type validation is not a virus scan. Storage is local disk.
- **Tika detection limits.** Some text formats (for example JSON) may be detected as another text type and refused.
  This errs on the side of rejection.
- **Local database user is a superuser.** The official PostgreSQL image makes `POSTGRES_USER` a superuser, and a
  superuser can disable triggers. The append-only guarantees hold against the application, not against a database
  administrator. The early architecture overview mentions a least-privilege database user. That is a production
  target (checklist item 4), not the local-dev state.
- **Seeded demo credentials.** `V2__seed_test_data.sql` creates accounts with a documented password.
- **Swagger UI is public** in the default configuration.
- **Frontend dependencies use caret ranges.** Commit `package-lock.json` and audit before release.

## 4. Production hardening checklist

1. Terminate TLS in front of the API. Keep `BTS_COOKIE_SECURE=true`. Configure forwarded-header handling only for a
   trusted proxy, and make the rate limiter see the real client address.
2. Generate new `BTS_JWT_SECRET` and database passwords (`openssl rand -base64 48`) and load them from a secret manager.
3. Do not ship `V2__seed_test_data.sql` to a production database (a fresh database never needs it).
4. Use two database roles: an owner for Flyway migrations, and a runtime role limited to DML on the application
   tables with no DDL, no TRIGGER and no TRUNCATE privilege.
5. Disable the documentation endpoints: `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.
6. Replace the in-memory limiter with a shared store, and add per-account throttling and alerting.
7. Scan uploads for malware, move storage to encrypted object storage, and back it up with the database.
8. Ship logs with their correlation ids, alert on spikes of `ACCESS_DENIED` and `LOGIN_FAILED`, and set an audit retention policy.
9. Serve the built SPA with the same CSP that `vite.config.ts` applies to `vite preview`.
10. Run `npm audit` and a Maven dependency scan in CI, and pin versions with a committed lockfile.
11. Add MFA/SSO (OIDC) before exposing the system to real users.
