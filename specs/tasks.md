# Implementation Plan

Ordered by dependency. Each task: tests first (from acceptance criteria), implement, run full suite + lint + type check, tick, log in `progress.md`, commit `type(scope): T-xxx … [IDs]`.

Commands: backend `cd backend && ./mvnw -q verify`; frontend `cd frontend && npm run lint && npm run typecheck && npm test -- --run`.

## Foundation

- [x] T-001: Scaffold repo — `git init` (main), `.gitignore`, `CLAUDE.md`; backend Maven project (Spring Boot 4.1, Java 21, wrapper) with `HrmApplication`, profiles (dev/test/prod), embedded-PostgreSQL test support (`IntegrationTest` base), context-loads test; frontend Vite + React + TS + Mantine + Vitest + ESLint scaffold with one passing test.
  - Requirements: NFR-020, NFR-006
  - Done when: `./mvnw verify` and `npm run lint && npm run typecheck && npm test -- --run` pass; first commit made.

- [x] T-002: Common infrastructure — base entity (UUID, version, audit columns, soft delete), problem+json exception handling, validation → 422, correlation-id filter + MDC, `PageResponse`, `Clock` bean, `HrmProperties`, `DatabasePlatform` SPI + `PostgresDatabasePlatform` (vendor migration location, sequence counter), Flyway V1 baseline, ArchUnit rules (module boundaries, no native SQL outside `platform`).
  - Requirements: NFR-005, NFR-006, NFR-008, NFR-009, NFR-013, NFR-014, NFR-015, NFR-016, NFR-017, NFR-018
  - Done when: tests for error format, correlation header echo, sequence counter concurrency, ArchUnit rules pass.

- [x] T-003: Security core — users/roles/permissions schema, seeded 9 roles + matrix, bootstrap super admin, login/refresh/logout/me, lockout, refresh rotation & reuse detection, password policy + change password, must-change gate, BCrypt, JWT config, security headers, CORS, login rate limit, security-event logging.
  - Requirements: FR-001, FR-002, FR-003, FR-005 (seed), NFR-002, NFR-003, NFR-019
  - Done when: API tests for every FR-001..003 criterion pass.

- [x] T-004: Audit module — append-only `audit_log` (+ PG trigger), `AuditService`, search endpoint, security events recorded.
  - Requirements: FR-008 (record/search/immutability)
  - Done when: tests prove UPDATE/DELETE rejected by DB, search filters & permission work.

## Organization & Employee (vertical slice 1)

- [x] T-005: Org module — companies (+ settings, initializer hook), branches, departments tree (cycle check), designations, grades, employment categories; soft-delete rules; audit.
  - Requirements: FR-010, FR-011, FR-012, FR-013, FR-016
  - Done when: API tests for each criterion pass.

- [x] T-006: User admin, roles CRUD, data scope — user CRUD/deactivate/reset, last-super-admin guard, custom roles & matrix, `DataScopeService` (descendant departments) + `/admin/permissions`.
  - Requirements: FR-004, FR-005, FR-006
  - Done when: API tests incl. scope filtering & 404-outside-scope pass.

- [x] T-007: Employee core — create/edit (optimistic lock), numbering formats, emergency contacts, reporting-manager validation, status lifecycle, employment history, search/filter/paging in scope, soft delete rule, salary-free DTOs.
  - Requirements: FR-014, FR-015, FR-017, FR-018, FR-007 (no salary in profile)
  - Done when: API tests for each criterion incl. concurrent numbering pass.

- [x] T-008: Custom fields + documents — field definitions & value validation; `FileStorage` + local impl; upload (size/type sniffing), list, download, soft delete.
  - Requirements: FR-019, FR-020
  - Done when: API tests pass.

- [x] T-009: Frontend shell — API client (token refresh, problem+json), auth context, login & change-password pages, permission-aware AppShell nav, i18n setup, reusable DataTable / form drawer / confirm components, routing.
  - Requirements: FR-001, FR-002, FR-003 (UI), NFR-010, NFR-012
  - Done when: Vitest tests for client refresh/error parsing, login form and nav filtering pass; lint & typecheck pass.

- [x] T-010: Frontend org setup & administration — companies/settings, branches, departments tree, lookups; users, roles matrix.
  - Requirements: FR-004, FR-005, FR-010…013, FR-016 (UI)
  - Done when: component tests for key forms pass; screens work against backend (manual run).

- [x] T-011: Frontend employee hub — list/search, create/edit tabs (personal, employment, contacts, custom fields), status change, history, documents.
  - Requirements: FR-014, FR-015, FR-017…020 (UI)
  - Done when: component tests pass; manual run against backend.

## Calendar, Shift & Roster

- [x] T-012: Holidays — backend CRUD, branch applicability, duplicate check, copy-year; frontend calendar/list.
  - Requirements: FR-025
  - Done when: API tests + component test pass.

- [x] T-013: Shifts, OT policies, rotation patterns & assignments (backend).
  - Requirements: FR-030, FR-031, FR-032
  - Done when: API tests for validation rules (night/split/flex) & overlap pass.

- [x] T-014: ScheduleResolver + `/schedules` endpoint.
  - Requirements: FR-033, FR-040 (data)
  - Done when: unit tests for precedence, holidays, weekly-off, rotation modulo pass.

- [x] T-015: Rosters backend — create, entries, bulk assign, copy, validate/conflicts, publish/lock/unlock, revisions, mark attendance for reprocessing hook.
  - Requirements: FR-035, FR-036, FR-037, FR-038, FR-039, FR-040
  - Done when: API tests for each criterion pass.

- [x] T-016: Frontend shift & roster — shifts, OT policies, rotations; roster grid (employees × dates), bulk assign, copy, publish/lock, revisions; my schedule.
  - Requirements: FR-030…040 (UI)
  - Done when: component tests pass; manual run.

## Attendance & Approvals

- [x] T-017: Attendance ingestion — sources & API keys, adapter SPI (generic JSON, CSV), ingestion endpoint, CSV import, idempotency, unmatched + rematch.
  - Requirements: FR-045, FR-046, FR-047
  - Done when: API tests pass.

- [x] T-018: `AttendanceCalculator` (pure) — window assignment, pairing, breaks, late/early/short, OT/extra, statuses, half-day leave, night & split shifts.
  - Requirements: FR-048
  - Done when: unit-test table covering each rule passes.

- [x] T-019: `AttendanceProcessor` — batch processing, triggers, nightly job, daily/events views, self-service attendance.
  - Requirements: FR-048, FR-055
  - Done when: integration tests (ingest → process → daily) pass.

- [x] T-020: Approval engine — workflows (most-specific resolution), transactions & level snapshot, eligibility, fallback, approve/reject/cancel/auto, inbox, my requests, delegation; seeded workflows via company initializer.
  - Requirements: FR-070, FR-071, FR-072, FR-073
  - Done when: API tests for each criterion pass (using a test handler).

- [x] T-021: Notifications — channel SPI, log & email channels, after-commit async dispatch, approval events.
  - Requirements: FR-074
  - Done when: tests prove dispatch after commit, failure isolation.

- [x] T-022: Attendance adjustments, lock, exceptions, OT approval.
  - Requirements: FR-050, FR-051, FR-052, FR-053, FR-054, FR-056
  - Done when: API tests pass (lock tested once payroll periods exist via repository fixture).

- [x] T-023: Frontend attendance & approvals — daily grid, day detail with edit/manual create & history, exceptions, CSV import, sources, OT approval; approval inbox, my requests, workflows config, delegations.
  - Requirements: FR-045…056, FR-070…073 (UI)
  - Done when: component tests pass; manual run.

## Leave

- [x] T-024: Leave types (seed), balances, opening/adjust, accrual / year-end / expiry jobs.
  - Requirements: FR-060, FR-061, FR-062
  - Done when: unit + API tests pass.

- [x] T-025: Leave applications — day counting, validations, approval handler, cancel, calendar, attendance reprocess, self-service leave endpoints.
  - Requirements: FR-063, FR-064, FR-065, FR-066 (data), FR-100 (leave)
  - Done when: API tests pass.

- [x] T-026: Frontend leave — types config, balances, apply (self & on behalf), requests, calendar.
  - Requirements: FR-060…065 (UI)
  - Done when: component tests pass.

## Payroll

- [x] T-027: Payroll config — components (validation incl. forward refs), slab tables, `StatutoryPack` SPI + `LK` pack, statutory endpoint, company initializer seeding.
  - Requirements: FR-080, FR-081, FR-010 (seeding)
  - Done when: API tests pass incl. seeded LK values.

- [x] T-028: Salary structures — effective dating, supersede, lines/exclusions.
  - Requirements: FR-082, FR-007
  - Done when: API tests pass.

- [x] T-029: `PayrollEngine` (pure) — FIXED/PERCENT_OF/PER_UNIT/SLAB, bases, proration, rounding.
  - Requirements: FR-085, FR-081 (no hard-coded rates), NFR-013
  - Done when: unit tests incl. LK worked examples pass.

- [x] T-030: Payroll periods & processing — period dates, one-time items, processor (attendance reprocess, inputs, results, exceptions), stale detection, review endpoints.
  - Requirements: FR-083, FR-084, FR-085, FR-086, FR-066
  - Done when: API tests pass.

- [ ] T-031: Payroll approval & finalization — submit → approval engine, send back, finalize, locked trigger, attendance lock integration.
  - Requirements: FR-087, FR-053
  - Done when: API + DB trigger tests pass.

- [ ] T-032: Exporters & payslips — PDF/Excel/CSV exporters, payslip PDF (single/bulk), email payslips, payroll register & department summary.
  - Requirements: FR-088, FR-089
  - Done when: tests parse generated PDF/XLSX/CSV and check figures.

- [ ] T-033: Frontend payroll — components, statutory settings, slab tables, salary structures, periods workflow, results review, one-time items, payslips & exports.
  - Requirements: FR-080…089 (UI)
  - Done when: component tests pass.

## Reports, Dashboard, Self-service

- [ ] T-034: Reports & dashboard backend — report catalog & 9 reports with exports, audit export, dashboard KPIs.
  - Requirements: FR-095, FR-096, FR-008 (export)
  - Done when: API tests pass.

- [ ] T-035: Frontend reports, dashboard, audit log.
  - Requirements: FR-095, FR-096, FR-008 (UI)
  - Done when: component tests pass.

- [ ] T-036: Self-service portal — remaining `/me` endpoints (profile, documents, schedule, payslips) + portal UI.
  - Requirements: FR-100, FR-040
  - Done when: API + component tests pass.

## Hardening & Delivery

- [ ] T-037: OpenAPI docs config, performance test (1,000 employees payroll), i18n bundle completeness check.
  - Requirements: NFR-004, NFR-007, NFR-010
  - Done when: perf test < 60 s; docs endpoint test passes.

- [ ] T-038: Packaging — Dockerfile (multi-stage, SPA embedded), compose on-prem/cloud, Caddy, backup/restore scripts, docs (install on-prem, cloud deploy, backup & recovery, release/update).
  - Requirements: NFR-001, NFR-002, NFR-011, NFR-021
  - Done when: `docker compose -f deploy/compose.onprem.yml up` serves login over HTTPS and backup script produces a dump that restores.

- [ ] T-039: E2E smoke (Playwright) — full monthly flow at desktop & mobile viewport.
  - Requirements: NFR-012, NFR-020
  - Done when: `npm run e2e` passes.
