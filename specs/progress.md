# Progress Log

## Status
- Phase: 6 — Implement
- Current task: T-009
- Next: T-010

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
