# Architecture

## 1. Layers

```text
Controllers (thin, @PreAuthorize)  ->  DTOs (validated)  ->  Services (@Transactional, rules)  ->  Repositories  ->  PostgreSQL
```

- **Controllers** only bind input, declare the authorisation gate and call one service method.
- **DTOs** are the only types that cross the API boundary. Entities never leave the service layer.
- **Services** own transactions, business rules, DTO mapping and audit records. `open-in-view` is disabled, so
  nothing lazy is touched after the transaction ends.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto: none`, and the integration tests exercise every mapping
  against the real migrated database.

## 2. Request lifecycle

```text
Browser (SPA :5173) --/api--> Vite proxy --> Spring Boot :8080

CorrelationIdFilter -> SecurityHeadersFilter
  -> Spring Security chain: CORS -> Origin check -> login rate limit -> JWT cookie authentication -> URL rules
  -> DispatcherServlet -> Controller (@PreAuthorize -> ProjectSecurity) -> Service -> Repository
Any exception -> GlobalExceptionHandler -> ApiError { status, code, message, correlationId }
```

The browser only talks to `localhost:5173`. The dev proxy forwards `/api`, so the API is same-origin from the
SPA's point of view. This is what lets `SameSite=Strict` and `Path=/api` work without relaxing any cookie attribute.

## 3. Data model

```text
users 1---* project_members *---1 projects
users 1---* bugs (reporter, assignee) *---1 projects          UNIQUE (project_id, bug_number), @Version
bugs  1---* bug_comments | bug_history | attachments           append-only / soft-archived
audit_log                                                      append-only, no foreign keys
```

All timestamps are `timestamptz` and the JVM, the JDBC session and the database run in UTC. The database enforces
what the code intends:

- `CHECK ((status = 'CLOSED') = (resolution IS NOT NULL))` makes an inconsistent close or reopen impossible.
- Triggers reject UPDATE, DELETE and TRUNCATE on `bug_comments`, `bug_history` and `audit_log`.
- Triggers reject hard DELETE on `users`, `projects`, `bugs` and `attachments`, so archival is the only way to remove data.
- Email is lower-cased by a CHECK constraint, project keys follow a pattern CHECK, and attachment size is capped.

## 4. Bug workflow

```text
OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED (resolution required)
                           |           |
                           v           v
                        REOPENED <-----+      REOPENED -> IN_PROGRESS   (resolution cleared)
```

`BugStateMachine` is the single source of truth. The API returns `allowedTransitions`, already filtered by the state
machine and the caller's project role, so the UI only offers moves the server will accept. The server re-validates every
request, and illegal transitions return HTTP 400.

| Action | REPORTER | DEVELOPER | PROJECT_LEAD | ADMINISTRATOR |
|---|---|---|---|---|
| View, create, comment, attach | yes | yes | yes | yes |
| Edit fields | own bugs | yes | yes | yes |
| Transition status | no | yes | yes | yes |
| Assign, archive bug, manage members | no | no | yes | yes |
| Create project | no | no | no | yes |

## 5. Concurrency

- **Stale client copy.** Every write carries the `version` the client last read. A mismatch is HTTP 409.
- **Simultaneous writers.** If two requests pass the version check together, Hibernate's `@Version` fails the second
  flush, which is also HTTP 409. The losing transaction rolls back entirely, including its history and audit rows.
- **Bug numbering.** Creation takes a pessimistic row lock on the project, so concurrent creators never receive the same
  number. The `(project_id, bug_number)` unique constraint is the backstop.

## 6. Audit semantics

`AuditService.record` joins the caller's transaction, so an audit row can never describe a change that was rolled
back. Calls made outside a transaction (denied access, login events) open their own. Business history
(`bug_history`) and security audit (`audit_log`) are separate ledgers: the first is shown to users, the second to operators.

## 7. Error contract

Every failure is `{ timestamp, status, error, code, message, correlationId, path, fieldErrors? }`. Codes such as
`VALIDATION_FAILED`, `INVALID_TRANSITION`, `ACCESS_DENIED`, `NOT_FOUND`, `CONCURRENT_MODIFICATION`, `FILE_REJECTED` and
`RATE_LIMITED` are stable and meant to be switched on by clients. Messages are authored by the services and are safe to
show. Framework and database detail goes to the server log, next to the same correlation id.

## 8. List queries

Pagination is 0-based and capped at 100 rows. Sorting accepts only `createdAt`, `updatedAt`, `title`, `status`,
`priority` and `bugNumber`. Priority sorts by severity rank (Blocker to Low), not alphabetically. Filtering and search
use the Criteria API with escaped wildcards, and `ALPHA-12` or `12` also matches by bug number.

## 9. Front end

- **Routing.** `/login` is public. Everything else sits under `ProtectedRoute` and `AppShell`: dashboard, projects,
  project bug list, bug detail. The route guard is a convenience, and the server enforces access on every call.
- **State.** The signed-in user lives in memory only (`AuthContext`) and is restored from `GET /api/auth/me` after a reload.
  Nothing is written to `localStorage` or `sessionStorage`. A 401 from any call ends the session and returns to login.
- **Data loading.** `useApi` aborts in-flight requests when inputs change, so a slow response cannot overwrite a newer one.
  The bug-list state (filters, sort, page) lives in the URL, so it is shareable and survives reload and Back.
- **Rendering.** Server text is rendered as React text nodes. Markdown is rendered without raw HTML, with allow-listed link
  schemes and no remote images.
- **Design system.** Colour tokens live in `tailwind.config.js` (Midnight Mirage `#04243C`, Teal Blue `#2B9CAE`, severity
  colours as specified). Solid button fills use a darker teal so white text meets WCAG AA, and severity badges always carry a text label.
- **Accessibility.** Skip link, labelled controls, `aria-live` regions, `aria-sort` on sortable headers, a focus-trapping
  modal that restores focus, and reduced-motion support.

## 10. Design decisions and trade-offs

| Decision | Reason | Cost |
|---|---|---|
| Stateless JWT in a cookie, user re-read per request | Revocation of disabled users is immediate and no session store is needed | One cheap primary-key lookup per request |
| Markdown sanitiser instead of an HTML sanitiser library | Input is Markdown, not HTML. Tag openers are neutralised and code stays byte-exact | Rendering must keep raw HTML disabled (the SPA does) |
| Titles stored as plain text, not entity-escaped | React renders text nodes. Control characters are stripped server-side | Any other client must also treat titles as text |
| Soft delete and append-only enforced in the database | Holds even if application code has a bug | Needs a separate owner role in production to be a real guarantee |
| In-memory login limiter | Meets the brief with no extra infrastructure | Single node only |
| Local filesystem for attachments | Simple, with strong file permissions and opaque names | Not replicated. Production should use object storage |
