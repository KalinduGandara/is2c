# Premium HRM System — Claude context

Spec-driven project. **Source of truth: `specs/`** — read `specs/tasks.md` and `specs/progress.md` first, then continue from the first unchecked task.
- `specs/clarifications.md` — decisions & assumptions (CL-xx)
- `specs/requirements.md` — FR/NFR IDs with EARS acceptance criteria
- `specs/design.md` — architecture, data model, API, algorithms
- `specs/tasks.md` — ordered plan; `specs/progress.md` — log + traceability
- SRS: `docs/IS_2_C_Premium_HRM_System_SRS.md`

## Stack
- Backend: Java 21, Spring Boot 4.1 (WebMVC, Security, Data JPA, Flyway), PostgreSQL 16+ — `backend/`
- Frontend: React 19, Vite 8, TypeScript 6.0, Mantine 9, TanStack Query, React Router 8, react-i18next — `frontend/`
- Tests: JUnit 5 + MockMvc + embedded PostgreSQL (zonky, no Docker needed), ArchUnit; Vitest + Testing Library; Playwright E2E
- Packaging: Docker multi-stage + Compose (`deploy/`)

## Commands
- Backend full check: `cd backend && ./mvnw -q verify` (perf tests: `./mvnw verify -Pperf`)
- Backend single test: `cd backend && ./mvnw -q test -Dtest=ClassName`
- Frontend: `cd frontend && npm run lint && npm run typecheck && npx vitest --run`
- Frontend dev: `npm run dev` (proxies `/api` → `localhost:8080`)
- E2E: `cd frontend && npm run e2e`

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
