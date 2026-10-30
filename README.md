# Enterprise Jira Lite

Enterprise bug tracking system with project-scoped RBAC, an enforced bug workflow, optimistic concurrency
control, an append-only audit trail and a high-density React front end.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | React 18, TypeScript, Tailwind CSS, Lucide icons, Vite |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL 16) |
| API docs | OpenAPI 3 / Swagger UI |

## Quick start (local)

Prerequisites: JDK 21, Node.js 18.18+ (20 LTS recommended), Docker with Compose v2, and Maven 3.8+ (needed once,
to create the Maven wrapper).

```bash
cp .env.example .env                       # local-dev values; use fresh secrets for anything shared
docker compose up -d                       # PostgreSQL 16 on 127.0.0.1:5432
cd backend && mvn -N wrapper:wrapper -Dmaven=3.9.9 && ./mvnw spring-boot:run     # API on :8080 (Flyway runs on start-up)
cd frontend && npm install && npm run dev                                        # UI on :5173 (second terminal)
```

Open http://localhost:5173 and sign in with a demo account. The step-by-step verification, with expected output at
every stage, is the Production Execution Runbook delivered with the generated project.

## Demo accounts

Every account uses the password `Enterprise#Demo2026!`. They are seeded by `V2__seed_test_data.sql`, which is for
development only (see `docs/SECURITY.md`).

| Role | Email | Project access |
|---|---|---|
| ADMINISTRATOR | `admin@enterprise-demo.test` | All projects |
| PROJECT_LEAD | `lead.alpha@enterprise-demo.test` | ALPHA |
| DEVELOPER | `dev.alpha@enterprise-demo.test` | ALPHA |
| REPORTER | `reporter.alpha@enterprise-demo.test` | ALPHA |
| PROJECT_LEAD | `lead.beta@enterprise-demo.test` | BETA |
| DEVELOPER | `dev.beta@enterprise-demo.test` | BETA |

## Repository layout

```text
.
├── docker-compose.yml          PostgreSQL 16 (loopback only, health-checked, named volume)
├── .env.example                local-development configuration template
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/enterprise/bts/
│       │   ├── config/         properties, SecurityConfig, OpenAPI
│       │   ├── domain/         JPA entities and enums
│       │   ├── repository/     Spring Data repositories, query specifications
│       │   ├── security/       JWT, cookie, principal, ProjectSecurity (BOLA gate), login rate limiter
│       │   ├── service/        business rules, state machine, sanitiser, file validation, audit
│       │   ├── exception/      domain exceptions
│       │   └── web/            controllers, DTOs, filters, GlobalExceptionHandler
│       ├── main/resources/     application.yml, Flyway migrations (V1 schema, V2 seed)
│       └── test/               unit tests and Testcontainers integration/AppSec tests
├── frontend/                   Vite + React + TypeScript SPA
├── docs/                       SECURITY.md, ARCHITECTURE.md
└── assemble_project.py         rebuilds this tree from a saved transcript and zips it
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `BTS_DB_NAME` / `BTS_DB_USER` / `BTS_DB_PASSWORD` / `BTS_DB_PORT` | `bts` / `bts_app` / (required) / `5432` | Docker Compose database settings |
| `BTS_DB_URL` | `jdbc:postgresql://localhost:5432/bts` | JDBC URL used by the backend. Update it if you change the port |
| `BTS_JWT_SECRET` | (required) | HMAC signing key, at least 32 characters. Start-up fails otherwise |
| `BTS_JWT_TTL_MINUTES` | `30` | Session lifetime |
| `BTS_COOKIE_SECURE` | `true` | `Secure` flag on the session cookie. Safari does not accept it on plain http://localhost |
| `BTS_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | The single allowed browser origin (also used by the Origin check) |
| `BTS_UPLOAD_DIR` | `./var/uploads` | Attachment storage, outside any served directory |
| `VITE_API_BASE_URL` | `/api` | API base path as seen by the browser. Keep it relative |
| `BTS_API_PROXY_TARGET` | `http://localhost:8080` | Where the Vite dev/preview proxy forwards `/api` |

The backend reads the repo-root `.env` automatically when started from `backend/`. Real environment variables win.

## Testing

```bash
cd backend
./mvnw -Dtest='BugStateMachineTest,MarkdownSanitizerTest,FileValidationServiceTest,LoginRateLimitFilterTest' test   # no Docker
./mvnw clean test                                                                                              # full suite; needs Docker
cd ../frontend && npm run build                                                                                # type-check + production build
```

The integration suite starts a real PostgreSQL 16 through Testcontainers, applies the real Flyway migrations and
drives the full security filter chain through MockMvc. It covers BOLA/IDOR rejection on every project-scoped
endpoint, illegal state transitions, SQL-injection and XSS inputs, upload abuse and concurrent-update conflicts.

## API

Swagger UI: http://localhost:8080/swagger-ui.html. OpenAPI JSON: http://localhost:8080/v3/api-docs.
Every error uses one envelope (`status`, `code`, `message`, `correlationId`, optional `fieldErrors`), and the same
`correlationId` is in the `X-Correlation-Id` response header and in the server logs.

## Documentation

- `docs/ARCHITECTURE.md`: layers, data model, state machine, concurrency, request lifecycle, front-end structure.
- `docs/SECURITY.md`: control map, threat boundaries, known limitations and the production hardening checklist.

## 12. Deliverables & Acceptance Criteria Index

All primary project artifacts, architectural decisions, and visual proof of acceptance criteria are organized under the [`docs/`](docs/) directory.

### Core Documentation
* **Architecture & System Design:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (C4 context, components, data flows, trust boundaries, and ADRs)
* **Security Note & Threat Model:** [`docs/SECURITY.md`](docs/SECURITY.md) (STRIDE analysis, implemented controls, residual risks, and dependency summary)
* **OpenAPI 3.0 Contract:** [`docs/openapi.json`](docs/openapi.json) (Live interactive UI available at `/swagger-ui.html`)
* **Automated Test Report:** [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md) (329/329 passing tests: unit, integration, concurrency, and security suites)
* **Demonstration Walkthrough Script:** [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) (Step-by-step 5–10 minute oral evaluation guide)
* **Database Migrations:** [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration/) (`V1__init_schema.sql` and `V2__seed_test_data.sql`)

### Acceptance Criteria Evidence (Screenshots)
* **[AC-01] BOLA & Horizontal Isolation:** [`docs/screenshots/AC-01%20(BOLA%20test)/`](<docs/screenshots/AC-01 (BOLA test)/>)
  * Proves cross-project access rejection (`403 Forbidden` with Correlation ID).
* **[AC-02] State Machine & Assignment Control:** [`docs/screenshots/AC-02%20System%20rejects%20invalid%20state%20transition.../`](<docs/screenshots/AC-02 System rejects invalid state transition.../>)
  * Proves invalid state transition rejections and mandatory resolution enforcement before `CLOSED`.
* **[AC-03] Filtering, Search & Server Pagination:** [`docs/screenshots/AC-03%20(Search,%20Filters,%20and%20Pagination)/`](<docs/screenshots/AC-03 (Search, Filters, and Pagination)/>)
  * Proves multi-parameter filtering (status, priority, keyword) and backend pagination controls.
* **[AC-04] Chronological & Immutable Activity Timeline:** [`docs/screenshots/AC-04%20(Chronological,%20Immutable%20Activity...)/`](<docs/screenshots/AC-04 (Chronological, Immutable Activity...)/>)
  * Proves append-only audit trail logging actors, field changes, and relative timestamps.
* **[AC-05] Stored XSS Neutralization:** [`docs/screenshots/AC-05%20(Stored%20XSS%20Defense)/`](<docs/screenshots/AC-05 (Stored XSS Defense)/>)
  * Proves HTML tag entity encoding and malicious scheme blocking (`javascript:` $\rightarrow$ `#blocked`).
* **API Documentation UI:** [`docs/screenshots/Swagger_UI.png`](docs/screenshots/Swagger_UI.png)
  * Screenshot of live interactive Swagger UI endpoints and schemas.
