# Clarifications

Source: `docs/IS_2_C_Premium_HRM_System_SRS.md` (v1.0)

Status: **Closed (2026-10-07).** All questions answered. Each question lists a recommended default. "Accept defaults" adopts all defaults not explicitly overridden.

---

## A. Scope & architecture (highest impact)

**A1. Build scope for this effort.** The SRS describes a 6-phase roadmap (§13) and an MVP list (§13.1).
- Default: **MVP modules (§13.1)** + the approval engine (needed by leave/attendance corrections). Defer: full dashboard suite (§6) beyond basic reports, licensing, multi-tenant cloud, scheduled email reports.
- Why: §13.1 is the client's stated MVP; the full roadmap is many months of work.

**A2. Cloud tenant isolation (§7 "tenant isolation strategy").**
- Default: **One app instance + one database per customer** in both on-prem and cloud (isolation by deployment). No shared-DB multi-tenancy in v1.
- Why: Same code path for both editions, strongest isolation, simplest; true multi-tenancy is roadmap Phase 6.

**A3. Companies per installation.** §4.1 says "company profile and multi-branch"; §3 has a "Company Administrator" role.
- Default: **Multiple companies (group of legal entities) per installation**, each with branches; data scoped by company → branch → department.
- Why: Common for SME groups; retrofitting a company dimension later is expensive.

**A4. Authentication.**
- Default: **Built-in username/password** (BCrypt, JWT access + refresh tokens via Spring Security). No external IdP / SSO in v1; MFA out of scope.
- Why: On-prem installs often lack an IdP; keeps install self-contained.

**A5. Frontend in scope?**
- Default: **Yes — React + Vite + TypeScript SPA** for all MVP screens including Employee self-service portal.

**A6. Database.** §7 says "SQL Server or PostgreSQL"; §7.1 recommends PostgreSQL.
- Default: **PostgreSQL only** (16+). Why: Supporting two RDBMSs doubles migration/testing effort.

## B. Payroll

**B1. Country / statutory rules.** §14 says don't hard-code statutory values.
- Default: **Sri Lanka as the seed configuration** (EPF 8%/12%, ETF 3%, APIT slab table) delivered as editable data, not code.

**B2. Payroll component calculation model.**
- Default: Components use a fixed set of **calculation types**: FIXED amount, PERCENT_OF (basic / gross / another component), PER_UNIT (hours/days × rate, e.g. OT, no-pay days), SLAB (tiered table, e.g. tax). No free-form scripting/formula language in v1.
- Why: Configurable without code changes, safe, and testable.

**B3. Payroll frequency & currency.**
- Default: **Monthly** periods only, configurable cut-off day; **single currency per company** (LKR seed); amounts `NUMERIC(15,2)`, rounding HALF_UP to 2 dp.

**B4. No-pay / attendance deduction basis.**
- Default: Daily rate = (Basic [+ configurable components]) / configurable divisor (default 30); per-hour rate = daily rate / shift hours.

**B5. Payslip delivery.**
- Default: PDF payslips downloadable in portal and emailable individually/in bulk; **not password-protected** in v1.

## C. Attendance & shifts

**C1. Biometric middleware.**
- Default: Generic **REST ingestion endpoint** (API-key secured) + **CSV file import**, both mapping into a standard `AttendanceEvent` via an adapter interface. A TimePro-specific adapter only if you can provide its API spec.

**C2. Shift types in v1.** §4.3 lists 6; §12 acceptance lists only fixed/rotating/overnight.
- Default: **All six** (fixed, flexible, rotating, night, split, custom).

**C3. Night shift attendance date.**
- Default: Attendance belongs to the **shift start date**; punches matched to the shift window ± configurable tolerance.

**C4. Overtime rules.**
- Default: Policy per shift/category: OT starts after shift end (or after required hours for flexible), minimum OT minutes threshold, rounding unit, rate multipliers per day type (normal / weekly-off / public holiday), optional pre-shift OT; OT may require approval (configurable).

**C5. Payroll-impacting attendance correction approval.**
- Default: Approval workflow is **configurable on/off** per transaction type; when on, a correction takes effect only after final approval.

## D. Leave

**D1. Leave year & accrual.**
- Default: Leave year = **calendar year**; accrual methods per leave type: annual upfront grant or monthly accrual; pro-rata for mid-year joiners; carry-forward cap and expiry date per type.

**D2. Weekends/holidays inside a leave range.**
- Default: Configurable per leave type whether weekly-offs/holidays are counted.

**D3. Negative balance.**
- Default: Not allowed except for No Pay leave (unlimited).

## E. Security, roles & audit

**E1. Roles.**
- Default: The **9 SRS roles seeded**, plus admins can create custom roles via a permission matrix (module × {View, Add, Edit, Delete, Approve, Export, Configure}). Data scope (all / company / branch / department / own) assigned per user.

**E2. Audit log immutability.**
- Default: Append-only table enforced at DB level (no UPDATE/DELETE grants for the app user + trigger guard); no edit UI.

**E3. Password policy.**
- Default: Min 10 chars, mixed character classes, lockout 15 min after 5 failed attempts; admin-initiated reset.

## F. Non-functional

**F1. Scale & performance targets.**
- Default: Up to **5,000 employees** per installation; p95 API < 1 s for screens, < 5 s for common reports; payroll run for 1,000 employees < 60 s.

**F2. Backups.**
- Default: Scheduled `pg_dump` via a container job/script + documented restore procedure; not an in-app feature.

**F3. Document storage.**
- Default: Local filesystem (configurable mount path) behind a storage interface; max 10 MB/file; PDF/JPG/PNG/DOCX.

**F4. Data retention.**
- Default: Keep indefinitely; soft-delete for business records; no automated purging in v1.

**F5. Localization.**
- Default: English only; frontend uses i18n keys (react-i18next) and backend messages via message bundles so Sinhala/Tamil can be added later.

**F6. Deployment packaging.**
- Default: **Docker Compose** (app + PostgreSQL + reverse proxy w/ HTTPS) for both editions; config via environment variables.

## G. Tooling & process

**G1. Build tooling.**
- Default: Monorepo `backend/` (Java 21, Spring Boot 3.x, **Maven**, Flyway, JUnit 5 + Testcontainers) and `frontend/` (React + Vite + TypeScript, Vitest + Testing Library, Playwright for E2E).

**G2. Git.** Repo is not a git repository yet.
- Default: Run `git init` on `main` and commit per task as the workflow specifies.

---

## Answers

Round 1 (2026-10-07):
- A1: MVP §13.1 + approval engine (default accepted)
- A2: One app instance + DB per customer (default accepted)
- A3: Multiple companies per installation (default accepted)
- A4: Built-in username/password + JWT (default accepted)
- A6 (override): **PostgreSQL only for now, but persistence must sit behind interfaces so adding another database vendor is easy.**
- B1 (override): **Statutory rules must be easily configurable (admin-editable data), with Sri Lankan values as the default.**
- All other questions (A5, B2–B5, C1–C5, D1–D3, E1–E3, F1–F6, G1–G2): defaults accepted.
- User instruction: proceed directly through requirements/design/tasks into implementation without stopping at approval gates (user may review spec files at any time).

## Agreed assumptions

1. Build scope = SRS §13.1 MVP modules + approval engine + basic dashboard KPIs (§11.1). Full §6 dashboard suite, licensing, shared-DB multi-tenancy, scheduled email reports, user manuals are deferred.
2. Isolation = one app instance + one PostgreSQL database per customer, identical image for on-prem and cloud; edition selected by configuration.
3. Multiple companies per installation; data scope hierarchy company → branch → department → own.
4. Built-in authentication (BCrypt + JWT access token + rotating refresh token). No SSO/MFA in v1.
5. DB portability (A6): domain code uses JPA/JPQL and repository interfaces only; vendor-specific SQL lives only in `db/migration/{vendor}` and `DatabasePlatform` implementations; enforced by an architecture test.
6. Statutory (B1): EPF/ETF/APIT and similar are ordinary payroll components and slab tables flagged `statutory`, created from a pluggable `StatutoryPack` (default `LK` — Sri Lanka) when a company is created, and editable via the Statutory Settings screen/API. No statutory numbers in calculation code.
7. Seeded Sri Lanka values (editable, customer must verify against current law): EPF employee 8 %, EPF employer 12 %, ETF 3 % of EPF-liable earnings; APIT monthly table (from 1 Apr 2025): 0–150,000 @0 %, next 83,333 @6 %, next 41,667 @18 %, next 41,667 @24 %, next 41,667 @30 %, balance @36 %.
8. Integration tests use embedded PostgreSQL (zonky) because Docker is not guaranteed on developer machines; Docker Compose is used for deployment packaging.
9. Spring Boot serves the built React SPA (single deployable jar/image); Caddy reverse proxy terminates HTTPS in the Compose bundle.


---

## Round 2 (2026-10-07) — seed defaults surfaced while writing the spec

| # | Question | Answer |
|---|---|---|
| R2-1 | APIT seed table | Table effective 1 Apr 2025 (relief 150,000/month; 6 % on next 83,333; 18 %/24 %/30 % on next 41,667 each; 36 % balance). |
| R2-2 | Default PAYROLL_APPROVAL approver / finalizer | HR_MANAGER (Payroll Officer prepares; separation of duties). |
| R2-3 | Company Administrator payroll visibility | None by default (SRS §3 — sensitive payroll restricted). |
| R2-4 | Default OT multipliers (hourly = Basic ÷ 240) | Workday 1.5×, weekly-off 1.5×, public holiday 2.0×. |

Other notes:
- Docker is now available (Rancher Desktop, `DOCKER_HOST=unix://~/.rd/docker.sock`). Decision: keep embedded PostgreSQL for automated tests (no Docker dependency, real PG 17 binaries); Docker used for Compose packaging and container smoke test.
