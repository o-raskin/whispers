# Scope
- This repository is a backend-only Java service for authenticated 1:1 chat.
- Do not assume any frontend, mobile client, or checked-in browser app exists here.
- Limit changes to code and assets that actually exist in this repository.

# Repository facts
- The backend is framework-light and uses custom HTTP and WebSocket infrastructure.
- The repo does not use Spring, Hibernate, or an ORM.
- Main technologies:
  - Java 25
  - Maven
  - Jackson
  - PostgreSQL JDBC
  - Liquibase
  - JUnit 5
- Public contract is documented in `swagger.yaml`.
- Main entry points:
  - `src/main/java/com/oraskin/App.java`
  - `src/main/java/com/oraskin/ChatServer.java`

# Repository structure
- `src/main/java/com/oraskin/auth` — OIDC flow, token issuance, refresh/logout, auth persistence
- `src/main/java/com/oraskin/chat` — chat services, repositories, DTOs, browser-key support
- `src/main/java/com/oraskin/common` — reusable HTTP, MVC, JSON, PostgreSQL, and WebSocket infrastructure
- `src/main/java/com/oraskin/resource` — transport-facing controllers
- `src/main/java/com/oraskin/user` — user persistence, profile lookup, session registry
- `src/main/java/com/oraskin/websocket` — runtime message dispatch, presence, typing
- `src/main/resources/db/changelog` — Liquibase master changelog and incremental migrations
- `src/test/java/com/oraskin` — service and migration-focused tests
- `swagger.yaml` — public HTTP and WebSocket contract

# Non-negotiable rules
- Read the affected flow end-to-end before editing.
- Make the smallest correct change.
- Prefer existing repository patterns over inventing new ones.
- Do not introduce frameworks, generic base abstractions, or new infrastructure layers unless explicitly requested.
- Do not perform broad cleanup, package reshuffling, or stylistic refactors unless explicitly requested.
- Match nearby code style, naming, and structure.
- Treat code as source of truth when docs drift.

# Architecture rules
- Keep controllers thin.
- Controllers may parse input, call services, and shape responses.
- Business logic belongs in services.
- Persistence and row mapping belong in repositories/stores.
- Do not put business rules, authorization decisions, or chat policy into JDBC code.
- Put reusable transport/runtime code in `common` only when it is genuinely shared.
- Preserve the current split:
  - HTTP plumbing in `common.http` and `common.mvc`
  - WebSocket protocol code in `common.websocket`
  - domain logic in `auth`, `chat`, `user`, and related domain packages

# Coding rules
- Prefer immutable `record` types for DTOs and domain-view values when consistent with existing code.
- Keep mutable POJOs only where the repo already uses them intentionally.
- Use constructor injection only. Dependency wiring is manual in `ChatServer`.
- Prefer explicit types and small methods.
- Keep payload shapes stable unless the task explicitly changes the contract.
- If controller binding behavior changes, update `ControllerMethodInvoker` when required.
- Use:
  - `ChatException` for expected client-facing errors
  - `IllegalStateException` for infrastructure/runtime failures

# Contract rules
- `swagger.yaml` is mandatory for public contract changes.
- Any change to a public endpoint or WebSocket command is incomplete unless `swagger.yaml` is updated in the same change.
- Update `swagger.yaml` when changing:
  - endpoint paths
  - request shapes
  - response shapes
  - DTO fields
  - WebSocket commands or event shapes
  - auth header behavior
  - auth cookie behavior
- Protected HTTP endpoints use bearer tokens.
- Refresh uses the `whispers_refresh_token` cookie.
- WebSocket auth may use `Authorization` or `Sec-WebSocket-Protocol`.
- `PRIVATE` chat is browser-key-bound. Do not weaken key-binding behavior unless explicitly instructed.

# Database and migration rules
- PostgreSQL is required for runtime behavior.
- Liquibase runs during startup.
- Add schema changes as new files under `src/main/resources/db/changelog/ddl`.
- Include new migrations from `db.changelog-master.xml`.
- Preserve append-only migration history.
- Do not rewrite old migrations unless the task is specifically migration repair.
- Keep SQL explicit and readable.
- Do not hide simple repository queries behind new abstractions.

# Validation rules
- Use:
  - `mvn clean`
  - `mvn test`
- Run the narrowest relevant tests first.
- Run `mvn test` after meaningful backend changes, especially when shared infrastructure changes.
- Add or update tests for service behavior changes.
- Add or update migration tests when schema changes are introduced.
- Prefer existing test helpers and fakes, including `src/test/java/com/oraskin/chat/TestSupport.java`.

# Runtime notes
- Local PostgreSQL is typically started with `docker compose up -d postgres`.
- Common environment variables include:
  - `WHISPERS_DB_URL`
  - `WHISPERS_DB_USER`
  - `WHISPERS_DB_PASSWORD`
  - `WHISPERS_FRONTEND_BASE_URL`
  - `WHISPERS_AUTH_TOKEN_TTL_SECONDS`
  - `WHISPERS_AUTH_REFRESH_TOKEN_TTL_SECONDS`
  - `WHISPERS_AUTH_TOKEN_ISSUER`
  - `WHISPERS_AUTH_POST_LOGOUT_REDIRECT_URI`
  - `WHISPERS_OIDC_GOOGLE_ISSUER`
  - `WHISPERS_OIDC_GOOGLE_REDIRECT_URI`
  - `WHISPERS_GOOGLE_CLIENT_ID`
  - `WHISPERS_GOOGLE_CLIENT_SECRET`
- Preferred local run path: start PostgreSQL, set env vars, run `com.oraskin.App`.
- Do not assume Docker build validates tests; the Dockerfile compiles with `javac` directly and skips test execution.

# Known gaps
- No HTTP routing integration suite
- No WebSocket integration suite
- No auth/OIDC integration coverage
- No end-to-end DB-backed repository suite

# Required change workflow
1. Read the affected controller, service, repository/store, DTOs, tests, and contract files.
2. Identify whether the change also requires updates to:
  - `swagger.yaml`
  - Liquibase migrations
  - tests
  - `ChatServer` wiring
3. Implement the smallest safe change.
4. Run relevant tests. Use `mvn test` when shared code is touched.
5. Report:
  - behavior changed
  - contract changed or not
  - migration changed or not
  - tests run
  - remaining risks or unverified areas

# Do
- Keep controllers thin.
- Keep business rules in services.
- Keep repository code focused on persistence.
- Keep public contract and implementation aligned.
- Preserve existing package boundaries.
- Prefer local, safe refactors over wide rewrites.

# Don’t
- Don’t invent frameworks or architecture layers.
- Don’t move business logic into repositories.
- Don’t change public API shapes without updating `swagger.yaml`.
- Don’t assume missing frontend code exists elsewhere in the repo.
- Don’t perform repo-wide cleanup unless explicitly asked.
- Don’t treat README text or local build output directories as authoritative over source code.