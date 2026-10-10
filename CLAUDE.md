# Premium HRM System — Claude context

Spec-driven project. **Source of truth: `specs/`** — read `specs/tasks.md` and `specs/progress.md` first, then continue from the first unchecked task.
- `specs/clarifications.md` — decisions & assumptions (CL-xx)
- `specs/requirements.md` — FR/NFR IDs with EARS acceptance criteria
- `specs/design.md` — architecture, data model, API, algorithms
- `specs/tasks.md` — ordered plan; `specs/progress.md` — log + traceability
- SRS: `docs/IS_2_C_Premium_HRM_System_SRS.md`

## Stack
- Backend: Java 21, Spring Boot 4.1 (WebMVC, Security, Data JPA, Flyway), PostgreSQL 16+ — `backend/`
- Frontend: React 19, Vite 8, TypeScript 6.0, Mantine 9 (+ spotlight), TanStack Query, React Router 8, react-i18next — `frontend/`
- Tests: JUnit 5 + MockMvc + embedded PostgreSQL (zonky, no Docker needed), ArchUnit; Vitest + Testing Library; Playwright E2E + axe accessibility (`e2e/a11y.spec.ts`)
- Packaging: Docker multi-stage + Compose (`deploy/`)

## Commands
- Backend full check: `cd backend && ./mvnw -q verify` (perf tests: `./mvnw verify -Pperf`)
- Backend single test: `cd backend && ./mvnw -q test -Dtest=ClassName`
- Frontend: `cd frontend && npm run lint && npm run typecheck && npx vitest --run`
- Frontend dev: `npm run dev` (proxies `/api` → `localhost:8080`)
- Local backend (no Docker): `cd backend && ./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.is2c.hrm.DevHrmApplication` → :8080, admin / Admin#Pass123 (forced change)
- E2E: `cd frontend && npm run e2e` (starts its own backend :8081 with a fresh DB and Vite :5174)
- Frontend full check: `cd frontend && ./check.sh`; backend test totals: `backend/test-summary.sh`
- Docker (from repo root): `cp deploy/.env.example deploy/.env` → `docker compose -f deploy/compose.onprem.yml up -d --build` (HTTPS via Caddy; cloud: `compose.cloud.yml`); backup `docker compose -f deploy/compose.onprem.yml exec backup /scripts/backup.sh`; ops docs in `docs/`

## Conventions
- Base package `com.is2c.hrm.<module>` with `domain/` (entities, repositories), `service/`, `web/` (controllers, DTO records), optional `api/` (cross-module interfaces). Cross-module calls only via `api` or services; enforced by ArchUnit.
- DB portability (NFR-005): no native SQL / JdbcTemplate outside `com.is2c.hrm.platform`; vendor DDL in `src/main/resources/db/migration/postgresql/`.
- No statutory or policy values in code — they are seeded data (FR-081).
- REST under `/api/v1`, errors as problem+json (`common.web.ApiExceptionHandler`), permissions `@PreAuthorize("hasAuthority('MODULE:ACTION')")`, data scope via `DataScopeService`.
- Critical changes call `AuditService.record(...)` in the same transaction.
- Money: `BigDecimal`, scale 2, HALF_UP. Time: inject `Clock`.
- Tests reference requirement IDs in `@DisplayName("FR-xxx …")`; write tests first from acceptance criteria.
- Commit per task: `feat(<module>): T-xxx <summary> [FR-xxx, …]`.
- UI strings go through i18n keys (`frontend/src/i18n/en.json`).
- UI: theme in `frontend/src/theme.ts` + `src/styles/global.css`; reuse `src/components/` (`PageHeader`, `DataTable`, `CrudPanel`, `StatusBadge`, `EmptyState`, `PageLoader`, `StatCard`, `useUrlTab`, `format.ts`). Never show raw enums (use `StatusBadge`/`humanize`), confirm destructive actions, give every list a loading and empty state, keep pages usable at 360 px and in dark mode; E2E selectors use accessible names, so report label changes.
