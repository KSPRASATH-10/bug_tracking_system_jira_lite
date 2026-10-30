# Enterprise Bug Tracking System — 5 to 10 Minute Demonstration Script

This walkthrough script is designed for live evaluators or recorded project demonstrations. It systematically proves system setup, core functional workflows (F1–F5), authorization/BOLA defenses (AC-01), lifecycle invariants (AC-02), and security defenses (AC-04, AC-05).

---

## 1. System Setup & Architecture Overview (~1 min)

* **Speaker Script:** 
  > "Hello everyone. Today I am presenting Enterprise Jira Lite, a production-grade bug and issue tracking system built using Spring Boot 3.3.5 with Java 21, React with Vite, and a PostgreSQL 16 relational database. 
  > The database schema is version-controlled using Flyway migrations with append-only audit triggers, and state transitions use optimistic concurrency locking via JPA `@Version`. Let's start the application."

* **Actions to Show:**
  1. Open terminal and run `docker compose ps` to show healthy PostgreSQL.
  2. Start backend (`mvn spring-boot:run`) and frontend (`npm run dev`).
  3. Navigate to `http://localhost:5173` to display the sign-in screen.

---

## 2. Authentication & Multi-Tenancy Project Isolation / AC-01 (~1.5 mins)

* **Speaker Script:**
  > "Authentication uses stateless JWT tokens stored in secure, HttpOnly, SameSite=Strict cookies to eliminate cross-site token exfiltration. 
  > First, I will demonstrate multi-tenancy access control and Broken Object Level Authorization (BOLA) defenses. 
  > I am logging in as 'dev.beta@enterprise-demo.test'—a developer assigned exclusively to Project Beta."

* **Actions to Show:**
  1. Sign in with `dev.beta@enterprise-demo.test` / `Enterprise#Demo2026!`.
  2. Notice the dashboard displays only **Project Beta**.
  3. Manually paste Project Alpha's direct URL into the browser:
     `http://localhost:5173/projects/20000000-0000-4000-8000-0000000000a1`
  4. Show the immediate **Access Denied (403 Forbidden)** screen with a unique Correlation ID.
  5. Mention that all project boundaries are strictly validated on the backend via project membership resolvers, never relying solely on hidden UI elements.

---

## 3. Bug Lifecycle, Finite State Machine & Resolutions / AC-02 (~2 mins)

* **Speaker Script:**
  > "Now I will sign in as 'lead.alpha@enterprise-demo.test', who has Project Lead permissions on Project Alpha. 
  > We enforce a finite state machine where bugs cannot skip lifecycle phases: OPEN moves to IN_PROGRESS, then to RESOLVED, and finally CLOSED or REOPENED. Furthermore, moving to CLOSED strictly requires selecting a resolution."

* **Actions to Show:**
  1. Sign out and sign in as `lead.alpha@enterprise-demo.test`.
  2. Select **Project Alpha: Settlement Portal**.
  3. Click **+ New bug** and create a bug titled `Automated Reconciliation Timeout`, priority `HIGH`. Show it initializes in `OPEN` state with key `ALPHA-<N>`.
  4. Open the status dropdown and show that only valid transitions (e.g. `IN_PROGRESS`) are permitted.
  5. Transition the bug from `OPEN` to `IN_PROGRESS`.
  6. Transition the bug from `IN_PROGRESS` to `RESOLVED`.
  7. Open bug `ALPHA-3` (which is already `RESOLVED`) and click `CLOSED`.
  8. Point out the modal prompt demanding a resolution (`Fixed`, `Cannot Reproduce`, etc.) before allowing the change.

---

## 4. Search, Filtering, and Server-Side Pagination / AC-03 (~1 min)

* **Speaker Script:**
  > "On high-volume enterprise systems, searching and filtering must be performant and handled entirely on the backend to avoid loading massive datasets into browser memory."

* **Actions to Show:**
  1. Go to the Project Alpha bug table.
  2. In the search box, type a keyword (e.g., `report` or `Safari`).
  3. Filter by **Priority: High** and **Status: In progress**.
  4. Point out the dynamic table refresh and pagination summary in the footer (`Showing 1–... Rows [20]`).

---

## 5. Audit Timeline & Stored XSS Neutralization / AC-04 & AC-05 (~1.5 mins)

* **Speaker Script:**
  > "Every single change made to a bug is permanently tracked. The database uses append-only triggers that prevent any modification or deletion of timeline events. 
  > In addition, comments and descriptions support rich Markdown, but all inputs undergo rigorous server-side HTML entity sanitization to neutralize stored cross-site scripting attacks."

* **Actions to Show:**
  1. Open bug `ALPHA-2`.
  2. Scroll down to the **Activity** timeline and show previous events: creator, assignment changes, and status transitions with exact relative timestamps.
  3. In the comment box, paste a hostile payload:
     ```markdown
     Security audit check: <script>alert('pwned')</script> and [Hostile Link](javascript:alert(1))
     ```
  4. Click **Post comment**.
  5. Show that no script pops up. The `<script>` tags render as inert text (`&lt;script>`) and the link protocol is rewritten to `#blocked`.

---

## 6. API Contract & Automated Test Suite Verification (~1 min)

* **Speaker Script:**
  > "To wrap up, the entire REST API is fully specified via OpenAPI 3.0 and live at our Swagger UI endpoint. 
  > Our automated test suite includes 329 comprehensive unit, integration, and security tests covering BOLA, SQL injection defense, optimistic concurrency, and rate limiting—all passing with zero failures."

* **Actions to Show:**
  1. Open `http://localhost:8080/swagger-ui.html` in a tab to display the API docs.
  2. Switch to the terminal and highlight the Maven test summary line:
     `Tests run: 329, Failures: 0, Errors: 0, BUILD SUCCESS`.

---

## 7. Conclusion (~30 secs)

* **Speaker Script:**
  > "In summary, Enterprise Jira Lite satisfies all functional requirements, security boundaries, and enterprise testing standards outlined in the specification. Thank you for your time, and I am happy to take any questions."