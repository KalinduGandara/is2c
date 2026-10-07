# Progress Log

## Status
- Phase: 6 — Implement
- Current task: T-016
- Next: T-017

## Log
- 2026-10-07: Phases 1–5 complete (clarifications closed, requirements/design/tasks written). User waived approval gates ("start implementing").
- 2026-10-07: T-001 done — Spring Boot 4.1.1 backend + embedded PG test base; Vite/React/Mantine frontend; lint/typecheck/tests green.
- 2026-10-07: T-002 done — base entities (UUIDv7, version, soft delete), problem+json handler, correlation id, HrmProperties, DatabasePlatform SPI + Postgres sequence, ArchUnit rules (no JDBC/native SQL outside platform, web layer module-private). 14 tests green.
- 2026-10-07: T-003 done — JWT (HS256, clock-aware validation) login/refresh rotation+reuse detection/logout/me/change-password, lockout, password policy, must-change gate, bootstrap admin, 9 seeded roles, security headers/CSP, login rate limit. 37 tests green.
- 2026-10-07: T-004 done — append-only audit_log (PG trigger), AuditService (in-transaction), security events audited (incl. @PreAuthorize denials via AuthorizationDeniedEvent), search API. Audit export deferred to T-034 as planned. 44 tests green.
- 2026-10-07: T-005 done — companies (+defaults, CompanyCreatedEvent), settings, branches, department tree (cycle checks), lookups, DataScopeService in org, EmployeeNumberFormat. Delete-with-employees guard via OrgReferenceChecker SPI (exercised in T-007). 58 tests green.
- 2026-10-07: T-006 done — `admin` module: user CRUD/activate/deactivate/reset, last-super-admin guard, custom roles & matrix, escalation guards; scope-filtered user admin. 69 tests green.
- 2026-10-07: T-007 done — employee master (numbering w/ concurrency, validations, manager cycle check, status lifecycle, employment history, soft delete via usage SPI, scoped search). Default-shift validation deferred to T-013 (shift module). 87 tests green.
- 2026-10-07: T-008 done — custom field defs (typed validation, hidden-when-inactive), FileStorage SPI + local impl, content-sniffed documents (PDF/JPEG/PNG/DOCX, 10 MB), download as attachment, soft delete. 94 tests green.
- 2026-10-07: T-009 done — frontend shell: API client (problem+json, single-flight refresh), auth context, login/change-password, permission-aware AppShell nav, company context, i18n, DataTable. Refresh token kept in sessionStorage (cleared on tab close). 12 frontend tests green.
- 2026-10-07: T-010 done — generic EntityForm/CrudPanel; Organization setup (companies, settings, branches, department tree, lookups, custom fields); Administration (users w/ scope + reset/activate, roles permission matrix, audit search). 20 frontend tests green. Manual run against backend deferred to T-011 (dev runner).
- 2026-10-07: T-011 done — Employee Hub (list/search/filters, profile form w/ contacts + custom fields, status change, history, documents). Dev launcher `DevHrmApplication` (embedded PG, no Docker); Playwright E2E harness (own backend :8081 + Vite :5174, fresh DB) with first smoke spec passing; E2E caught duplicate i18n key (fixed + bundle test). Test-support moved to `com.is2c.testsupport` so dev runs don't load test beans.
- 2026-10-07: T-012 done — holiday calendar (branch applicability, overlap duplicate check, copy-year w/ Feb 29 skip, visible to company employees) + Holidays tab. 98 backend / 27 frontend tests green.
- 2026-10-07: T-013 done — shifts (6 types, midnight/split/flex rules, deactivate-only), OT policies (min/round/cap/approval), rotation patterns + assignments (overlap checks), employee default-shift validation via EmployeeExtension; pure ShiftDefinition/OtPolicyDefinition in shift.api. 112 tests green.
- 2026-10-07: T-014 done — pure ScheduleRules (roster > rotation > default/weekly-off; holidays → HOLIDAY keeping shift) + batch ScheduleResolver + GET /schedules. Fixed two latent bugs found by the API test: weekday-set converter turned NULL into empty set (overrode company weekly-off), and Hibernate dropped null OFF days in rotation patterns (V9 adds explicit off_day flag). 119 tests green.
- 2026-10-07: T-015 done — rosters (weekly/monthly periods, department subtree, cell & bulk edits, copy by offset, conflict detection: cross-roster duplicates / next-day overlap / inactive shift, publish/lock/unlock, revision history, ScheduleChangedEvent, PublishedRosterEntries for schedules). Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES disabled globally (omitted booleans default false). Test fixture field renamed `orgs` (avoids shadowing `org.*` packages). 128 tests green.
