# Progress Log

## Status
- Phase: 6 — Implement
- Current task: T-005
- Next: T-006

## Log
- 2026-10-07: Phases 1–5 complete (clarifications closed, requirements/design/tasks written). User waived approval gates ("start implementing").
- 2026-10-07: T-001 done — Spring Boot 4.1.1 backend + embedded PG test base; Vite/React/Mantine frontend; lint/typecheck/tests green.
- 2026-10-07: T-002 done — base entities (UUIDv7, version, soft delete), problem+json handler, correlation id, HrmProperties, DatabasePlatform SPI + Postgres sequence, ArchUnit rules (no JDBC/native SQL outside platform, web layer module-private). 14 tests green.
- 2026-10-07: T-003 done — JWT (HS256, clock-aware validation) login/refresh rotation+reuse detection/logout/me/change-password, lockout, password policy, must-change gate, bootstrap admin, 9 seeded roles, security headers/CSP, login rate limit. 37 tests green.
- 2026-10-07: T-004 done — append-only audit_log (PG trigger), AuditService (in-transaction), security events audited (incl. @PreAuthorize denials via AuthorizationDeniedEvent), search API. Audit export deferred to T-034 as planned. 44 tests green.
