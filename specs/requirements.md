# Requirements — Premium HRM System (v1 / MVP)

Source: `docs/IS_2_C_Premium_HRM_System_SRS.md` v1.0 (cited as §n). Decisions: `specs/clarifications.md` (cited as CL-xx).

Conventions
- IDs are stable. Never renumber; retire an ID by marking it **(withdrawn)**.
- "THE SYSTEM" = backend API + web UI. Unless stated, API rules are enforced server-side; the UI mirrors them.
- "Authorized user" = user holding the stated permission `MODULE:ACTION` (FR-005) **and** whose data scope covers the target record (FR-006).
- Validation failures return HTTP 400/422 with a problem+json body (NFR-009). Permission failures return 403. Missing records (or records outside scope) return 404.

---

## 1. Security, Users & Audit (§3, §7, §10, §12 Security)

### FR-001 Login — §3, §7, CL-A4
As a user, I want to sign in with username and password so that I can use the system.
- WHEN valid credentials for an active user are submitted, THE SYSTEM SHALL return a signed JWT access token (default lifetime 15 min) and an opaque refresh token (default 7 days).
- WHEN invalid credentials are submitted, THE SYSTEM SHALL return 401 with a generic message that does not reveal whether the username exists.
- WHEN 5 consecutive failed attempts occur for a username, THE SYSTEM SHALL lock the account for 15 minutes and reject logins (even with correct password) during the lock with 401 and an "account locked" code.
- WHEN a deactivated user attempts to log in, THE SYSTEM SHALL reject the login with 401.
- THE SYSTEM SHALL record login success, failure and lockout as security events (NFR-008).

### FR-002 Token refresh & logout — §7
- WHEN a valid, unexpired, unrevoked refresh token is presented, THE SYSTEM SHALL issue a new access token and a new refresh token and revoke the presented one (rotation).
- WHEN a revoked refresh token is presented, THE SYSTEM SHALL reject it with 401 and revoke all refresh tokens of that user (reuse detection).
- WHEN a user logs out, THE SYSTEM SHALL revoke the presented refresh token.
- THE SYSTEM SHALL store refresh tokens only as SHA-256 hashes.

### FR-003 Password management — §10, CL-E3
- WHEN a password is set or changed, THE SYSTEM SHALL require ≥ 10 characters containing at least one upper-case letter, one lower-case letter, one digit and one non-alphanumeric character, and reject non-compliant passwords with 422.
- WHEN a user changes their own password, THE SYSTEM SHALL require the current password.
- WHEN an authorized admin (`ADMIN:EDIT`) resets a user's password, THE SYSTEM SHALL set a temporary password, flag the account `mustChangePassword`, and revoke the user's refresh tokens.
- WHILE `mustChangePassword` is set, THE SYSTEM SHALL reject all API calls except change-password, logout and "who am I" with 403 code `PASSWORD_CHANGE_REQUIRED`.
- THE SYSTEM SHALL hash passwords with BCrypt (cost ≥ 12).

### FR-004 User management — §3
- An authorized user (`ADMIN:ADD/EDIT`) SHALL be able to create, edit and deactivate users with: username (unique, case-insensitive), email, display name, linked employee (optional, at most one user per employee), roles (≥ 1), data scope (FR-006), active flag.
- WHEN a user is deactivated, THE SYSTEM SHALL revoke their refresh tokens.
- THE SYSTEM SHALL prevent deactivating or removing the last active user holding the Super Administrator role (422).
- WHEN the system starts with no users, THE SYSTEM SHALL create a Super Administrator from `HRM_ADMIN_USERNAME`/`HRM_ADMIN_PASSWORD` with `mustChangePassword=true`; IF those variables are absent, THE SYSTEM SHALL fail start-up with a clear message.

### FR-005 Roles & permission matrix — §3, CL-E1
- THE SYSTEM SHALL define permissions as `MODULE:ACTION` where MODULE ∈ {ORG, EMPLOYEE, ATTENDANCE, SHIFT, ROSTER, LEAVE, PAYROLL, APPROVAL, REPORT, AUDIT, ADMIN} and ACTION ∈ {VIEW, ADD, EDIT, DELETE, APPROVE, EXPORT, CONFIGURE}.
- THE SYSTEM SHALL seed nine system roles: SUPER_ADMIN, COMPANY_ADMIN, HR_MANAGER, PAYROLL_OFFICER, BRANCH_MANAGER, DEPARTMENT_MANAGER, SUPERVISOR, EMPLOYEE, AUDITOR, with the default matrix in `design.md` §6.2.
- An authorized user (`ADMIN:CONFIGURE`) SHALL be able to create, edit and delete custom roles by selecting permissions from the matrix. System roles' names cannot be changed or deleted; their permissions can be edited except SUPER_ADMIN (always all permissions).
- WHEN a user calls an endpoint, THE SYSTEM SHALL allow it only if the union of the user's role permissions includes the endpoint's required permission; otherwise 403 and a security event.
- The AUDITOR role SHALL have only VIEW and EXPORT permissions.

### FR-006 Data scope restriction — §3
- Each user SHALL have a data scope: ALL, COMPANY (set of companies), BRANCH (set of branches), DEPARTMENT (set of departments, including all descendant departments) or OWN (only the linked employee).
- WHEN a user lists or searches employee-related records (employees, attendance, leave, roster, payroll, reports, approvals config), THE SYSTEM SHALL return only records whose employee/company/branch/department falls within the user's scope.
- WHEN a user requests or modifies a single record outside their scope, THE SYSTEM SHALL respond 404.
- Self-service endpoints (`/api/v1/me/**`) SHALL always operate on the user's linked employee regardless of scope; IF the user has no linked employee, THE SYSTEM SHALL respond 404 for those endpoints.

### FR-007 Sensitive payroll data — §3
- THE SYSTEM SHALL expose salary structures, payroll results, payslips and payroll exports only to users with `PAYROLL:VIEW` (within scope), except that an employee may view their own finalized payslips via self-service.
- Employee-profile responses SHALL NOT contain salary information.
- Payroll lines SHALL NOT appear in non-payroll reports.

### FR-008 Audit log — §3, §5.2, §8, §14, CL-E2
- WHEN a critical change occurs, THE SYSTEM SHALL append an audit record containing: timestamp (UTC), acting user id/username, on-behalf-of user (delegation), action, entity type, entity id, before-values (JSON), after-values (JSON), reason (if provided), client IP and correlation id.
- Critical changes are: any create/update/delete of employees, salary structures, payroll components, statutory settings, payroll periods/results/adjustments, attendance records & adjustments, leave requests/balances/types, roster publish/lock/revisions, workflow definitions, approval actions, users, roles, company settings, lock overrides, and security events (login success/failure, lockout, permission denied, password change/reset).
- THE SYSTEM SHALL provide no API to update or delete audit records, and the database SHALL reject UPDATE and DELETE on the audit table (verified by test).
- An authorized user (`AUDIT:VIEW`) SHALL be able to search audit records by date range, user, entity type, entity id and action, paginated, newest first, and export them (`AUDIT:EXPORT`) to CSV/Excel/PDF.

---

## 2. Organization & Employee Master (§4.1, §8, §12 Employee)

### FR-010 Companies — §4.1
- An authorized user (`ORG:ADD/EDIT`) SHALL be able to create and edit companies: code (unique), legal name, registration number, address, phone, email, currency code (ISO 4217, default LKR), EPF employer number, ETF number, statutory pack code (default `LK`), time zone (default `Asia/Colombo`).
- WHEN a company is created, THE SYSTEM SHALL create its default settings (FR-016), default leave types (FR-060), default approval workflows (FR-070) and the statutory components of the chosen statutory pack (FR-081).
- Company creation SHALL require `ORG:CONFIGURE` and is restricted to users with scope ALL.

### FR-011 Branches — §4.1
- An authorized user SHALL be able to create, edit and soft-delete branches of a company: code (unique within company), name, address, phone.
- WHEN a branch with active employees is deleted, THE SYSTEM SHALL reject the deletion with 422.

### FR-012 Departments & cost centers — §2.1, §4.1, §8
- An authorized user SHALL be able to create, edit and soft-delete departments of a company with: code (unique within company), name, optional parent department (same company), optional branch, optional cost-center code, optional manager employee.
- WHEN a parent assignment would create a cycle, THE SYSTEM SHALL reject it with 422.
- WHEN a department with active employees or child departments is deleted, THE SYSTEM SHALL reject the deletion with 422.
- THE SYSTEM SHALL return departments as a tree for a company.

### FR-013 Designations, grades & employment categories — §4.1
- An authorized user SHALL be able to maintain per-company lookups: designations (code, title), grades (code, name, rank), employment categories (code, name, `otEligible` flag, default true). Codes are unique within the company. Soft delete is rejected (422) while referenced by an active employee.

### FR-014 Employee number generation — §4.1
- Each company SHALL have an employee-number format composed of literal text and tokens `{COMPANY}` (company code), `{BRANCH}` (branch code), `{YYYY}` (join year), `{SEQ:n}` (sequence zero-padded to n digits, 1 ≤ n ≤ 10). Default: `{COMPANY}{SEQ:5}`.
- WHEN an employee is created without an explicit number, THE SYSTEM SHALL generate the next number; the sequence SHALL be per company (and per branch/year when those tokens are present) and SHALL never produce duplicates under concurrent creation.
- WHEN an explicit employee number is supplied by a user with `EMPLOYEE:CONFIGURE`, THE SYSTEM SHALL accept it if unique within the company, else 422.
- A format without `{SEQ:n}` SHALL be rejected with 422.

### FR-015 Employee profile — §4.1, §12 Employee
- An authorized user (`EMPLOYEE:ADD/EDIT`) SHALL be able to create and edit employees with:
  - Personal: title, first name*, last name*, preferred name, NIC/passport number (unique within company when given), date of birth, gender, marital status, nationality, personal email, mobile phone, address.
  - Employment: company*, branch*, department*, designation*, grade, employment category*, join date*, probation end date, confirmation date, contract end date, reporting manager (employee in same company, not self, no cycle), default shift, weekly-off days, attendance badge/device ID (unique within company), status.
  - Emergency contacts: 0..n (name*, relationship*, phone*).
  - Custom field values (FR-019).
- Fields marked * are mandatory; WHEN missing, THE SYSTEM SHALL reject with 422 listing each field.
- Updates SHALL use optimistic locking; WHEN a stale version is submitted, THE SYSTEM SHALL respond 409.

### FR-016 Company settings — §1, §4.2, §4.6
- Each company SHALL have editable settings (`ORG:CONFIGURE`): default weekly-off days (default SUNDAY), employee-number format (FR-014), payroll period start day (1–28, default 1), absent-day-as-no-pay flag (default true), no-pay divisor (default 30), attendance punch window before/after shift in minutes (defaults 240/240), roster week-start day (default MONDAY).
- Changes SHALL be audited (FR-008) and apply to subsequent processing only.

### FR-017 Employee status lifecycle & employment history — §4.1, §8
- Employee status SHALL be one of ACTIVE, RESIGNED, TERMINATED, INACTIVE.
- WHEN an authorized user changes status to RESIGNED or TERMINATED, THE SYSTEM SHALL require an exit date (≥ join date) and reason; WHEN changed to INACTIVE ("deactivate"), a reason is required.
- WHEN any of company, branch, department, designation, grade, employment category, reporting manager or status changes, THE SYSTEM SHALL append an EmploymentHistory record with effective date (default today, may be supplied), the previous and new values, reason and acting user.
- THE SYSTEM SHALL create an initial EmploymentHistory record ("JOINED") on employee creation.
- Employees SHALL never be hard-deleted; `EMPLOYEE:DELETE` performs a soft delete only for employees with no attendance, leave or payroll records, else 422.

### FR-018 Employee search — §12 Employee, NFR-016
- An authorized user (`EMPLOYEE:VIEW`) SHALL be able to search employees by free text (matches number, first/last/preferred name, NIC, badge; case-insensitive, contains), and filter by company, branch, department, designation, employment category and status; results are paginated (default 20, max 200) and sortable by number, name, join date.
- Results SHALL be restricted to the user's data scope (FR-006).

### FR-019 Custom employee fields — §4.1
- An authorized user (`EMPLOYEE:CONFIGURE`) SHALL be able to define custom fields per company: key (unique, `[a-z][a-z0-9_]*`), label, type (TEXT, NUMBER, DATE, BOOLEAN, SELECT with option list), required flag, active flag, display order.
- WHEN an employee is saved, THE SYSTEM SHALL validate custom values against the type (and options for SELECT) and required flag, rejecting invalid values with 422 naming the field.
- Deactivating a field SHALL hide it from forms but keep stored values.

### FR-020 Employee documents — §4.1, CL-F3
- An authorized user (`EMPLOYEE:EDIT`) SHALL be able to upload documents for an employee with: document type (configurable list, default: NIC, Birth Certificate, Contract, Certificate, Other), title, optional expiry date and file.
- WHEN a file exceeds 10 MB or its detected content type is not PDF, JPEG, PNG or DOCX, THE SYSTEM SHALL reject it with 422.
- Authorized users (`EMPLOYEE:VIEW`) SHALL be able to list and download documents; an employee SHALL be able to list/download their own documents via self-service.
- Deletion (`EMPLOYEE:DELETE`) SHALL be a soft delete; the stored file is retained.
- File content SHALL be stored via the `FileStorage` interface (local filesystem implementation), never in the web root, with a random storage key.

---

## 3. Holidays, Shifts & Rotation (§4.2, §4.3, §8, §12 Shift)

### FR-025 Holiday calendar — §4.2, §8
- An authorized user (`ORG:CONFIGURE`) SHALL be able to maintain holidays per company: date, name, type (PUBLIC, MERCANTILE, COMPANY, OTHER), applies to all branches or a selected set of branches.
- Duplicate holiday (same company, date and overlapping branch applicability) SHALL be rejected with 422.
- An authorized user SHALL be able to copy a year's holidays to another year as a starting point (dates kept by month/day; Feb 29 skipped when absent).
- Holidays SHALL be visible to all authenticated users of that company (leave calendar).

### FR-030 Shift templates — §4.3, §12 Shift
- An authorized user (`SHIFT:ADD/EDIT`) SHALL be able to define shifts per company: code, name, type (FIXED, FLEXIBLE, NIGHT, SPLIT, CUSTOM), color, and:
  - FIXED / NIGHT / CUSTOM: one session (start time, end time).
  - SPLIT: exactly two non-overlapping sessions within the day window.
  - FLEXIBLE: earliest start, latest end, required work minutes.
  - Late grace minutes (default 0), early-departure grace minutes (default 0), break mode (FIXED = deduct break minutes when worked ≥ break threshold minutes; PUNCHED = breaks are gaps between punch pairs, no fixed deduction), break minutes, break threshold minutes, half-day threshold minutes (optional), OT policy (optional), punch window before/after override (optional).
- WHEN a session's end time ≤ start time, THE SYSTEM SHALL treat it as ending next day (cross-midnight); a NIGHT shift MUST cross midnight else 422; non-NIGHT shifts that cross midnight are allowed only for CUSTOM.
- Shifts in use SHALL be deactivated (not deleted) and remain valid for history.

### FR-031 Rotation patterns (rotating shifts) — §4.3
- An authorized user SHALL be able to define a rotation pattern: name and an ordered cycle of N days (1 ≤ N ≤ 62), each day either a shift or OFF.
- An authorized user SHALL be able to assign a rotation pattern to employees with a start date (cycle day 1) and optional end date; overlapping rotation assignments for the same employee SHALL be rejected with 422.
- WHEN no published roster entry exists for an employee/date covered by a rotation assignment, THE SYSTEM SHALL use the pattern day `((date − start) mod N)` as that day's schedule.

### FR-032 Overtime policies — §4.2, §4.3, CL-C4
- An authorized user (`SHIFT:CONFIGURE`) SHALL be able to define OT policies: name, minimum OT minutes (default 30), rounding unit minutes (default 15, round down), allow pre-shift OT (default false), maximum OT minutes per day (optional), requires approval (default false).
- (Pay multipliers are configured on payroll components, FR-082, not on the policy.)

### FR-033 Daily schedule resolution — §4.2, §4.3, §4.4
- For an employee and date, THE SYSTEM SHALL resolve the expected schedule in this precedence: (1) entry in a PUBLISHED or LOCKED roster, (2) active rotation assignment, (3) employee default shift with employee weekly-off days (falling back to company default weekly-off days).
- The day type SHALL be HOLIDAY if a holiday applies to the employee's company/branch on that date, else WEEKLY_OFF if the schedule is OFF (or the date is a weekly-off day under rule 3), else WORKDAY.
- WHEN no shift can be resolved for a WORKDAY, the day SHALL be WORKDAY with no shift ("unscheduled") and is reported as an attendance exception.

---

## 4. Roster Planning (§4.4, §5.4, §12 Roster)

### FR-035 Roster creation — §4.4
- An authorized user (`ROSTER:ADD`) SHALL be able to create a roster with: company, optional department (department-wise roster; includes descendant departments), period type WEEKLY (7 days starting on a configured week-start day) or MONTHLY (calendar month), start date, name. Status starts DRAFT.
- A roster SHALL contain entries (employee, date, shift or OFF) only for dates in its period and employees in its company (and department, if set).

### FR-036 Roster entry editing & bulk assignment — §4.4
- An authorized user (`ROSTER:EDIT`) SHALL be able to set/clear individual entries (employee-wise) and bulk-assign a shift or OFF to a set of employees over a date range, optionally restricted to selected weekdays.
- WHEN the roster is LOCKED, THE SYSTEM SHALL reject entry changes with 422.

### FR-037 Copy previous roster — §4.4
- WHEN an authorized user requests "copy from roster X" into a DRAFT roster of the same period type, THE SYSTEM SHALL copy entries of X by day offset (day i of X → day i of target) for employees still in scope and active, skipping days beyond the target period, and report counts of copied and skipped entries.

### FR-038 Conflict detection — §4.4
- WHEN entries are saved or a roster is published, THE SYSTEM SHALL reject (422 with a list of conflicts) any situation where:
  - the same employee/date appears in two PUBLISHED/LOCKED rosters (or the roster being published), or
  - two consecutive-day assignments of an employee overlap in time (e.g. a night shift ending 06:00 and a next-day shift starting before 06:00), or
  - an entry refers to an inactive shift.
- THE SYSTEM SHALL offer a "validate" endpoint returning conflicts without saving.

### FR-039 Publish, lock & revision history — §4.4, §5.4
- An authorized user (`ROSTER:APPROVE`) SHALL be able to publish a DRAFT roster (status PUBLISHED, revision 1) and lock a PUBLISHED roster (status LOCKED, no further changes). Unlock requires `ROSTER:CONFIGURE` and a reason (audited).
- WHEN an entry of a PUBLISHED roster is changed, THE SYSTEM SHALL require a reason, increment the roster revision number and store a revision record (employee, date, old value, new value, user, timestamp, reason).
- Authorized users SHALL be able to view revision history of a roster.
- WHEN a published roster entry changes, THE SYSTEM SHALL mark affected attendance days for reprocessing (FR-048).

### FR-040 Roster viewing — §4.4, §5.4
- Employees SHALL be able to view their own schedule for a date range (resolved per FR-033) via self-service.
- Users with `ROSTER:VIEW` SHALL be able to view rosters/schedules of employees in scope in a grid (employees × dates).

---

## 5. Attendance (§4.2, §5.2, §9, §12 Attendance)

### FR-045 Attendance event ingestion API — §4.2, §9, CL-C1
- THE SYSTEM SHALL expose an ingestion endpoint authenticated by a per-source API key (header `X-Api-Key`), accepting a batch (≤ 5,000) of events: external event id, device id, employee identifier (badge/device ID or employee number), timestamp (ISO-8601 with offset, or local time interpreted in company time zone), direction (IN, OUT, UNKNOWN).
- THE SYSTEM SHALL store each event as an AttendanceEvent; WHEN an event with the same (source, external id) already exists, THE SYSTEM SHALL skip it (idempotent) and count it as duplicate.
- WHEN the employee identifier cannot be matched, THE SYSTEM SHALL store the event as UNMATCHED and report it among exceptions; WHEN a matching employee is later assigned that badge, a "rematch" action SHALL link unmatched events.
- The response SHALL report counts: accepted, duplicates, unmatched, rejected (with per-row reasons for invalid rows).
- An authorized user (`ATTENDANCE:CONFIGURE`) SHALL be able to create/disable attendance sources (name, company, adapter type) and (re)generate API keys; keys are shown once and stored hashed.

### FR-046 CSV attendance import — §4.2, §9
- An authorized user (`ATTENDANCE:ADD`) SHALL be able to upload a CSV (columns: employee identifier, timestamp, direction optional, device id optional) for a company; THE SYSTEM SHALL apply the same matching, idempotency (hash of row content as external id) and reporting as FR-045.

### FR-047 Attendance adapter layer — §9, §14
- Event ingestion SHALL go through an `AttendanceSourceAdapter` interface that converts a vendor payload into the standard event model; the generic JSON and CSV adapters SHALL be implementations of that interface, selected by the source's adapter type. No vendor-specific logic outside adapter implementations.

### FR-048 Daily attendance processing — §4.2, §4.3, §8
- THE SYSTEM SHALL compute one AttendanceDaily per employee per date from events + schedule (FR-033) + approved leave + holidays + adjustments, as specified in `design.md` §7.2, producing: shift, day type, first IN, last OUT, scheduled minutes, worked minutes (after break deduction), late minutes, early-departure minutes, short minutes, OT minutes by day type (WORKDAY/WEEKLY_OFF/HOLIDAY), extra minutes, OT approved flag, absent fraction, leave fraction & paid flag, status (PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HALF_DAY_LEAVE, WEEKLY_OFF, HOLIDAY, INCOMPLETE, UNSCHEDULED), exception flags.
- Multiple punches per day SHALL be supported (paired chronologically using directions when present, otherwise alternating starting with IN).
- Night shift: punches belonging to a cross-midnight shift SHALL be attributed to the shift's start date (CL-C3).
- Late: WHEN first IN of a session is later than session start + late grace, late minutes = first IN − session start; else 0. Early departure symmetric with early grace. FLEXIBLE shifts have no late/early.
- Short minutes = max(0, scheduled (or required) minutes − worked minutes).
- OT (eligible employees): WORKDAY OT = minutes after session end (+ minutes before start if pre-shift OT allowed; FLEXIBLE: worked − required); WEEKLY_OFF/HOLIDAY OT = all worked minutes; THEN apply minimum (below minimum → 0), rounding down to unit, daily cap. WHEN the employee's category is not OT-eligible, raw OT minutes SHALL be recorded as extra minutes and OT = 0. WHEN the shift has no OT policy, OT = 0 and extra = 0.
- WHEN the OT policy requires approval, OT SHALL count for payroll only after approval (FR-056).
- Processing SHALL be triggered: on demand for a company/employee/date range (`ATTENDANCE:EDIT`), automatically nightly for the previous day, after events are ingested for affected days, after adjustments/leave approval/roster changes for affected days, and at the start of payroll processing for the period.
- Processing SHALL be idempotent: reprocessing with the same inputs yields identical results.
- THE SYSTEM SHALL NOT reprocess days in a locked period (FR-053).

### FR-050 Manual attendance creation — §4.2, §5.2
- An authorized user (`ATTENDANCE:ADD`) SHALL be able to create a manual attendance record for an employee/date with IN and OUT times (OUT may be next day) and a mandatory reason (non-blank, ≤ 500 chars); WHEN the reason is missing, THE SYSTEM SHALL reject with 422 and create nothing.
- The manual record SHALL be stored as an AttendanceAdjustment (type CREATE) and, once effective (FR-052), SHALL override punch-derived times for that day.

### FR-051 Manual IN/OUT editing with audit — §4.2, §5.2, §8, §14
- An authorized user (`ATTENDANCE:EDIT`) SHALL be able to change the IN and/or OUT time of an employee's day with a mandatory reason.
- THE SYSTEM SHALL store an AttendanceAdjustment with before values (first IN, last OUT, status), after values, reason, requester, timestamp; raw AttendanceEvents SHALL never be modified or deleted (original values preserved).
- THE SYSTEM SHALL write an audit record (FR-008) with before/after values and user identity for every adjustment.
- Users SHALL be able to view the adjustment history of any day in scope.

### FR-052 Attendance correction approval — §4.7, §5.2, CL-C5
- WHEN an enabled ATTENDANCE_CORRECTION workflow applies to the employee, THE SYSTEM SHALL create the adjustment in PENDING status and submit it for approval; the day's results SHALL be unchanged until approval.
- WHEN approved, THE SYSTEM SHALL mark the adjustment APPLIED and reprocess the day; WHEN rejected or cancelled, the adjustment is closed without effect.
- WHEN no enabled workflow applies, the adjustment SHALL be APPLIED immediately.
- Only one PENDING adjustment per employee/date is allowed (422 otherwise).

### FR-053 Attendance lock — §4.2, §5.1, §5.2
- WHEN a date falls within a payroll period of the employee's company whose status is IN_REVIEW, APPROVED or FINALIZED, THE SYSTEM SHALL reject attendance creation, edits, reprocessing, event-driven recalculation and leave changes affecting that date with 422 code `ATTENDANCE_LOCKED`.
- A user with `ATTENDANCE:CONFIGURE` MAY override the lock for a single adjustment by setting `overrideLock=true` with a reason; THE SYSTEM SHALL audit the override. Finalized payroll results SHALL remain unchanged.
- WHEN attendance changes for a date inside a DRAFT payroll period, THE SYSTEM SHALL mark that period `stale` (FR-086).

### FR-054 Attendance exceptions review — §5.1 step 3
- Users with `ATTENDANCE:VIEW` SHALL be able to list, for a company and date range within scope, days with: INCOMPLETE punches, ABSENT, late, early departure, UNSCHEDULED, unapproved OT, and unmatched events; filterable by exception type, department and employee.

### FR-055 Attendance viewing — §4.2
- Users with `ATTENDANCE:VIEW` SHALL be able to view daily attendance (grid by employee/date, filterable) and raw events of a day for employees in scope; employees SHALL view their own daily attendance and events via self-service.

### FR-056 OT approval — §4.2, CL-C4
- Users with `ATTENDANCE:APPROVE` SHALL be able to approve or reject OT for one or many AttendanceDaily records with pending OT within scope; approval sets `otApproved=true`; rejection sets OT minutes counted to 0 for payroll (stored `otRejected=true`). Both are audited and respect the lock (FR-053).

---

## 6. Leave Management (§4.5, §5.3, §12 Leave)

### FR-060 Leave types — §4.5, CL-D1, CL-D2, CL-D3
- An authorized user (`LEAVE:CONFIGURE`) SHALL be able to maintain leave types per company: code, name, paid flag, annual entitlement days (decimal, 0.5 steps), accrual method (ANNUAL_UPFRONT or MONTHLY), pro-rata for joiners flag, carry-forward max days (0 = none), carry-forward expiry months (0 = never), allow half day, allow negative balance, unlimited flag (no balance tracking), count weekly-offs flag, count holidays flag, attachment required above N days (optional), eligible employment categories (empty = all), active flag, color.
- On company creation THE SYSTEM SHALL seed: Annual (14, paid, upfront, pro-rata, CF max 0), Casual (7, paid, upfront, pro-rata), Medical (7, paid, upfront), No Pay (unpaid, unlimited), all editable.

### FR-061 Leave balances — §4.5
- THE SYSTEM SHALL maintain a balance per employee, leave type and leave year (calendar year) with: opening, entitled, carried forward, adjustments, taken, pending, expired; available = opening + entitled + carried forward + adjustments − taken − pending − expired.
- An authorized user (`LEAVE:EDIT`) SHALL be able to set opening balances and post manual adjustments (± days) with mandatory reason (audited).
- Users SHALL view balances in scope; employees view their own.

### FR-062 Accrual, carry-forward & expiry — §4.5
- ANNUAL_UPFRONT: on 1 January (or join date for joiners) THE SYSTEM SHALL credit entitled = annual days, pro-rated for joiners when enabled as `annual × remaining months (including join month) / 12`, rounded down to 0.5.
- MONTHLY: on the 1st of each month THE SYSTEM SHALL credit `annual / 12` (rounded to 2 dp) for each month the employee is employed (from the join month).
- Year-end: THE SYSTEM SHALL carry forward min(available, carry-forward max) to the next year; WHEN carry-forward expiry months > 0, carried-forward days not used by the end of that month SHALL be expired (taken days consume carried-forward first).
- These jobs SHALL run automatically (scheduled) and on demand (`LEAVE:CONFIGURE`) and be idempotent per employee/type/period.
- Only ACTIVE employees accrue; balances are created lazily when first needed.

### FR-063 Leave application — §4.5, §5.3
- An employee (self-service) or an authorized user on behalf of an employee (`LEAVE:ADD`) SHALL be able to apply for leave with: leave type, from date, to date, half-day part (MORNING/AFTERNOON; only when from = to and the type allows half day), reason, attachments (0..n, same rules as FR-020).
- THE SYSTEM SHALL compute requested days by iterating the dates and counting WORKDAY dates, plus WEEKLY_OFF/HOLIDAY dates only when the type's count flags are set; half day = 0.5.
- THE SYSTEM SHALL reject (422) WHEN: to < from; range spans two leave years; requested days = 0; the employee is not eligible for the type or not ACTIVE; an overlapping PENDING or APPROVED leave exists (overlap of half-days of different parts is allowed); available balance < requested and negative not allowed (unlimited types skip this check); attachment required but missing; any date is locked (FR-053).
- On success THE SYSTEM SHALL create the request in PENDING, add requested days to the balance's pending, and submit it to the approval engine (FR-071).

### FR-064 Leave approval outcomes — §4.5, §5.3
- WHEN a leave request is approved (final level), THE SYSTEM SHALL move days from pending to taken, set status APPROVED and reprocess attendance for its dates (days become ON_LEAVE / HALF_DAY_LEAVE).
- WHEN rejected, THE SYSTEM SHALL release pending days and set REJECTED (rejection comment mandatory, FR-071).
- WHEN the employee cancels a PENDING request, THE SYSTEM SHALL release pending days and set CANCELLED.
- WHEN an authorized user (`LEAVE:EDIT`) cancels an APPROVED request with a reason and no date is locked, THE SYSTEM SHALL return taken days, set CANCELLED and reprocess attendance.

### FR-065 Leave calendar — §4.5
- THE SYSTEM SHALL provide a calendar view for a date range showing holidays and APPROVED (and optionally PENDING) leaves; users with `LEAVE:VIEW` see leaves of employees in scope; employees without `LEAVE:VIEW` see only their own leaves and holidays.

### FR-066 Unpaid leave payroll impact — §4.5, §5.3
- Approved days of an unpaid leave type SHALL be counted as no-pay days by payroll (FR-085) for dates inside the payroll period.

---

## 7. Approval Workflow Engine (§4.7, §12 Approval)

### FR-070 Workflow definitions — §4.7
- An authorized user (`APPROVAL:CONFIGURE`) SHALL be able to maintain workflows: transaction type (LEAVE_REQUEST, ATTENDANCE_CORRECTION, PAYROLL_APPROVAL), company, optional branch, optional department, enabled flag, ordered levels 1..5, each with approver type REPORTING_MANAGER, ROLE (role code) or USER (specific user).
- WHEN resolving the workflow for a transaction, THE SYSTEM SHALL pick the enabled workflow with the most specific match (department > branch > company) for the requester employee's company/branch/department (department match includes ancestor departments); at most one workflow may exist per (type, company, branch, department) (422).
- On company creation THE SYSTEM SHALL seed: LEAVE_REQUEST (enabled, 1 level REPORTING_MANAGER), ATTENDANCE_CORRECTION (disabled, 1 level ROLE HR_MANAGER), PAYROLL_APPROVAL (enabled, 1 level ROLE HR_MANAGER).

### FR-071 Approval transactions — §4.7, §5
- WHEN a transaction is submitted and a workflow applies, THE SYSTEM SHALL create an ApprovalTransaction (type, reference id, requester user/employee, company, status PENDING, current level 1) and a snapshot of the workflow levels.
- Eligible approvers for a level: REPORTING_MANAGER → user linked to the requester employee's reporting manager; ROLE → active users with that role whose data scope covers the requester employee; USER → that user. The requester's own user is never eligible. WHEN a level has no eligible approver, THE SYSTEM SHALL fall back to active users with role HR_MANAGER whose scope covers the employee, and record the fallback.
- WHEN an eligible approver approves, THE SYSTEM SHALL record the action and advance to the next level or, at the last level, set APPROVED and invoke the module's approval handler.
- WHEN an eligible approver rejects, THE SYSTEM SHALL require a non-blank comment (422 otherwise), set REJECTED and invoke the handler.
- WHEN the requester cancels a PENDING transaction, THE SYSTEM SHALL set CANCELLED and invoke the handler.
- WHEN a non-eligible user attempts an action, THE SYSTEM SHALL respond 403. Actions on non-PENDING transactions SHALL return 409.
- WHEN no enabled workflow applies, THE SYSTEM SHALL auto-approve and invoke the handler immediately, recording an APPROVED transaction with action "AUTO".
- Every action (submit, approve, reject, cancel, auto) SHALL be stored in the transaction history with user, on-behalf-of, timestamp, level and comment, and audited.

### FR-072 Approval inbox — §4.7, §11.1
- Each user SHALL see a list of PENDING transactions for which they are currently an eligible approver (directly or as delegate), with type, requester, summary and submitted date, and a list of their own submitted transactions with status.

### FR-073 Delegation — §4.7
- A user SHALL be able to delegate their approvals to another active user for a date range (from ≤ to); an admin (`APPROVAL:CONFIGURE`) MAY create delegations for any user.
- WHILE today is within an active delegation's range, the delegate SHALL be eligible wherever the delegator is eligible, and actions SHALL record "on behalf of" the delegator. Delegation is not transitive.
- Self-delegation and overlapping delegations from the same delegator SHALL be rejected (422).

### FR-074 Notification hooks — §4.7, §9, CL-B5
- THE SYSTEM SHALL emit notification events: APPROVAL_REQUESTED (to current eligible approvers), APPROVAL_COMPLETED (APPROVED/REJECTED to requester), PAYSLIP_AVAILABLE (to employee).
- Notifications SHALL be dispatched through a `NotificationChannel` interface after the transaction commits; an EMAIL channel (SMTP) SHALL be used when `hrm.mail.enabled=true`, otherwise a LOG channel. Delivery failures SHALL be logged and SHALL NOT fail the business transaction.

---

## 8. Payroll (§4.6, §5.1, §12 Payroll, §14)

### FR-080 Payroll components — §4.6, CL-B2
- An authorized user (`PAYROLL:CONFIGURE`) SHALL be able to maintain payroll components per company: code (unique), name, kind (EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION), frequency (RECURRING, ONE_TIME), assignment (PER_EMPLOYEE = only when in salary structure / one-time item; AUTOMATIC = applies to all employees unless excluded), calculation type, evaluation sequence (int), base memberships (set of base names, e.g. `STATUTORY`, `TAXABLE`), prorate flag, payslip display flag, statutory flag, active flag.
- Calculation types:
  - FIXED: amount from the salary-structure line or one-time item.
  - PERCENT_OF: rate % × basis value, basis ∈ {`COMPONENT:<code>`, `BASE:<name>`, `GROSS`}.
  - PER_UNIT: units × unit rate, units from an attendance metric (OT_WORKDAY_HOURS, OT_OFFDAY_HOURS, OT_HOLIDAY_HOURS, EXTRA_HOURS, NO_PAY_DAYS, LATE_HOURS, SHORT_HOURS, ABSENT_DAYS, WORKED_DAYS); unit rate = fixed amount, or `basis value / divisor × multiplier` (an empty divisor uses the company's no-pay divisor setting).
  - SLAB: progressive slab table (FR-081) applied to the basis value.
- WHEN a component's basis refers to a component or base whose members have an equal or higher sequence, THE SYSTEM SHALL reject the configuration with 422 (no forward references/cycles).
- A system component BASIC (EARNING, FIXED, RECURRING, PER_EMPLOYEE, prorate, bases STATUTORY & TAXABLE, sequence 10) SHALL exist for each company and cannot be deleted.
- Components used by any payroll result can only be deactivated, not deleted.

### FR-081 Statutory configuration (Sri Lanka default) — §14, CL-B1
- THE SYSTEM SHALL provide a pluggable `StatutoryPack` interface; packs create statutory components and slab tables for a company. The default pack `LK` SHALL create (all editable, `statutory=true`):
  - EPF_EE: DEDUCTION, PERCENT_OF `BASE:STATUTORY`, 8 %.
  - EPF_ER: EMPLOYER_CONTRIBUTION, PERCENT_OF `BASE:STATUTORY`, 12 %.
  - ETF: EMPLOYER_CONTRIBUTION, PERCENT_OF `BASE:STATUTORY`, 3 %.
  - APIT: DEDUCTION, SLAB on `BASE:TAXABLE` using table `APIT_MONTHLY`: 0–150,000 @ 0 %; 150,000–233,333 @ 6 %; 233,333–275,000 @ 18 %; 275,000–316,667 @ 24 %; 316,667–358,333 @ 30 %; > 358,333 @ 36 %.
  - Standard non-statutory components: OT_WORKDAY (PER_UNIT OT_WORKDAY_HOURS, `COMPONENT:BASIC / 240 × 1.5`), OT_OFFDAY (× 1.5), OT_HOLIDAY (× 2.0), EXTRA_HOURS (`COMPONENT:BASIC / 240 × 1.0`), NO_PAY (DEDUCTION PER_UNIT NO_PAY_DAYS, `COMPONENT:BASIC / divisor × 1` where an empty divisor means the company's no-pay divisor setting (FR-016, default 30), bases STATUTORY & TAXABLE), BONUS, INCENTIVE, COMMISSION (EARNING FIXED ONE_TIME, TAXABLE), ALLOWANCE (EARNING FIXED RECURRING, TAXABLE), LOAN_DEDUCTION, SALARY_ADVANCE (DEDUCTION FIXED).
- An authorized user (`PAYROLL:CONFIGURE`) SHALL be able to view and edit statutory components (rates, basis, slab rows) on a "Statutory settings" screen; changes are audited and affect only payroll processed afterwards.
- Slab tables: name, rows (lower bound inclusive, upper bound exclusive or open, rate %); rows SHALL be contiguous and non-overlapping starting at 0 (422 otherwise).
- Calculation code SHALL contain no statutory rates or thresholds (enforced by code review & tests that change seeded values and observe changed results).

### FR-082 Salary structures — §4.6, §8
- An authorized user (`PAYROLL:ADD/EDIT`) SHALL be able to assign an employee salary structure with an effective-from date: basic salary (> 0), and lines (component, amount for FIXED; optional rate/multiplier override for PERCENT_OF/PER_UNIT; or `excluded=true` for an AUTOMATIC component).
- A new structure SHALL supersede the previous one from its effective date (previous gets effective-to = new effective − 1 day); history is retained. Effective dates inside FINALIZED periods are rejected (422).
- Payroll SHALL use the structure effective on the period end date (or on the exit date for leavers).

### FR-083 One-time items — §4.6
- An authorized user (`PAYROLL:EDIT`) SHALL be able to add, edit and remove one-time items (employee, a FIXED component with ONE_TIME frequency, amount > 0, note) for a payroll period in OPEN or DRAFT status; changes in DRAFT mark the period stale.

### FR-084 Payroll periods — §4.6, §5.1
- An authorized user (`PAYROLL:ADD`) SHALL be able to open a monthly payroll period for a company (year, month); start/end derive from the company's period start day (e.g. start day 21 → 21st of previous month to 20th of the month). Overlapping periods for a company SHALL be rejected (422).
- Status transitions: OPEN → DRAFT (process) → DRAFT (reprocess) → IN_REVIEW (submit; requires not stale, no blocking exceptions) → DRAFT (send back, comment) | APPROVED (approval engine PAYROLL_APPROVAL) → FINALIZED (finalize). Rejection in approval returns to DRAFT. Any other transition SHALL be rejected with 409.

### FR-085 Payroll calculation engine — §4.6, §5.1, §8, §12 Payroll
- WHEN an authorized user (`PAYROLL:EDIT`) processes an OPEN/DRAFT period, THE SYSTEM SHALL (1) reprocess attendance for all period dates (FR-048), (2) for each employee of the company with join date ≤ period end and (exit date empty or ≥ period start) and a salary structure, compute a PayrollResult, (3) replace any previous results of the period, (4) clear the stale flag, set DRAFT.
- Inputs per employee: salary structure, one-time items, attendance metrics aggregated over the period (OT hours by day type counting only approved OT where required, extra hours, no-pay days = absent fractions (if company absent-as-no-pay) + unpaid leave fractions, late hours, short hours, absent days, worked days).
- Evaluation: components (active, applicable to the employee) in ascending sequence; each line amount rounded HALF_UP to 2 dp; bases accumulate (+ earnings, − deductions that are members); GROSS = sum of earnings evaluated so far; negative computed amounts are set to 0.
- Proration: WHEN the employee joined or left within the period, prorated FIXED recurring components (incl. BASIC) SHALL be multiplied by employed calendar days / period calendar days.
- Result: gross earnings, total deductions, net pay = gross − deductions, employer contributions total, and lines (component code/name/kind, units, rate, basis, amount) as an immutable snapshot of names and values.
- Exceptions recorded per employee (non-blocking unless stated): no salary structure (employee skipped), negative net pay (blocking), unresolved attendance exceptions in period (non-blocking, count shown).
- Calculation SHALL be deterministic and use only configured data (no hard-coded rates).

### FR-086 Payroll review & stale detection — §5.1 steps 3–7
- Authorized users (`PAYROLL:VIEW`) SHALL view a DRAFT/IN_REVIEW/… period: per-employee results with line details, totals by component and by department, and exceptions.
- WHEN attendance, leave, salary structures or one-time items affecting a DRAFT period change, THE SYSTEM SHALL set the period `stale=true`; submission for review SHALL be rejected while stale (422).

### FR-087 Payroll approval & finalization — §4.6, §5.1, §8
- Submitting for review SHALL create a PAYROLL_APPROVAL approval transaction (FR-071); final approval sets APPROVED; rejection returns to DRAFT.
- An authorized user (`PAYROLL:APPROVE`) SHALL finalize an APPROVED period → FINALIZED; THE SYSTEM SHALL mark results locked, after which any attempt to modify results, lines, one-time items or reprocess SHALL be rejected (API 409; database rejects UPDATE/DELETE of locked result rows — verified by test).
- Payroll period status changes SHALL be audited.

### FR-088 Payslips — §4.6, §6, §12 Payroll
- For FINALIZED periods, authorized users (`PAYROLL:VIEW`) SHALL generate/download an individual payslip PDF per employee and a bulk PDF (one payslip per page) for all or filtered employees; payslips show company, period, employee number/name/designation/department, earnings, deductions, gross, net, employer contributions (configurable display), attendance summary (worked days, no-pay days, OT hours) and a payslip number unique per company.
- Payslip content SHALL be generated only from the immutable result snapshot; repeated generation SHALL yield the same figures.
- Employees SHALL list and download their own payslips for FINALIZED periods via self-service.
- An authorized user (`PAYROLL:EXPORT`) SHALL be able to email payslips (individual or bulk) to employees with an email address; per-recipient success/failure SHALL be reported; disabled mail yields 422 with a clear message.

### FR-089 Payroll exports — §4.6, §6
- Authorized users (`PAYROLL:EXPORT`) SHALL export the payroll register of a period (rows = employees, columns = components + totals) to Excel, CSV and PDF, and a department summary to Excel/PDF.

---

## 9. Reports & Dashboard (§6, §11, §12 Reports)

### FR-095 Basic reports — §6, §13.1 "Basic Reports"
- THE SYSTEM SHALL provide these reports, each filtered by company and the user's scope, viewable on screen and exportable to PDF, Excel and CSV (`REPORT:EXPORT`):
  1. Employee list (filters as FR-018).
  2. Daily attendance (date; status, shift, IN/OUT, late, early, OT per employee).
  3. Attendance summary (date range; per employee present/absent/half/leave days, late count & minutes, early count, OT hours by type).
  4. Attendance edits (date range; adjustments with before/after, reason, user).
  5. Leave balances (year, leave type).
  6. Leave usage (date range; approved leaves).
  7. Roster (period; employee × date shift codes).
  8. Payroll register & department summary (requires `PAYROLL:VIEW`) — same as FR-089.
  9. Audit log (requires `AUDIT:VIEW`) — same as FR-008.
- Viewing requires `REPORT:VIEW` (plus the module permission noted). PDF output SHALL be print-friendly (A4, landscape for wide reports, header with company, report name, filters, generated time and user, page numbers).

### FR-096 Dashboard KPIs — §11.1
- THE SYSTEM SHALL provide a dashboard (scope-filtered) with: total active employees, present today, absent today, late arrivals today, employees on leave today, my pending approvals count, current payroll period status, last processed period OT cost, headcount by department. Payroll figures appear only for users with `PAYROLL:VIEW`.

---

## 10. Self-Service (§3 Employee, §14)

### FR-100 Employee self-service portal — §3, §14
- A user linked to an employee SHALL be able, via the responsive web portal, to: view own profile and documents, view own attendance & events, view own schedule, view own leave balances, apply for and cancel own leave, view own leave history, view and download own finalized payslips, manage own approval delegations (FR-073), change own password.

---

## 11. Non-Functional Requirements (§7, §10)

| ID | Requirement | Source | Verification |
|---|---|---|---|
| NFR-001 | Single code base and single deployable (jar / container image) for on-prem and cloud editions; edition and all environment-specific values configured via environment variables (`HRM_*`, Spring properties); no code branches by customer. | §7, CL-A2 | Config test; Compose files for both editions use the same image. |
| NFR-002 | HTTPS for all browser/API traffic in both editions (Caddy reverse proxy in Compose; HSTS enabled in cloud edition); app refuses to start in `prod` profile without a JWT secret ≥ 32 bytes. | §7, §10 | Startup test; Compose config review. |
| NFR-003 | Passwords hashed with BCrypt cost ≥ 12; API keys and refresh tokens stored hashed; no secrets in logs. | §10 | Unit tests. |
| NFR-004 | Performance (≤ 5,000 employees): p95 < 1 s for list/detail APIs with default page size; common reports < 5 s for 1,000 employees × 1 month; payroll processing (incl. attendance reprocessing) < 60 s for 1,000 employees × 1 month on a 4-vCPU/8 GB server. | §10, CL-F1 | Performance integration test (payroll 1,000 employees) + index review. |
| NFR-005 | Database portability: domain modules access data only via repository interfaces using JPA/JPQL; vendor-specific SQL exists only in `db/migration/{vendor}` and `DatabasePlatform` implementations selected by `hrm.db.vendor`; PostgreSQL 16+ is the supported implementation. | §7, CL-A6 | ArchUnit test (no native queries / vendor SQL outside platform package). |
| NFR-006 | Schema changes only via versioned Flyway migrations; app validates schema on start (`ddl-auto=validate`). | §10, §15 | Startup test. |
| NFR-007 | REST API under `/api/v1`, documented with OpenAPI 3 at `/api/docs` (UI at `/api/docs/ui`), disabled in prod unless `HRM_API_DOCS=true`. | §7, §9, §10 | Integration test fetches spec. |
| NFR-008 | Logging: structured (JSON in prod) application logs with correlation id per request (`X-Correlation-Id` echoed); errors logged with stack traces server-side only; security events logged to a dedicated `SECURITY` logger and audit log. | §10 | Tests on correlation header & security events. |
| NFR-009 | Error responses use RFC 9457 `application/problem+json` with `type`, `title`, `status`, `detail`, `code`, `correlationId` and, for validation, `errors[] {field, message}`; no stack traces in responses. | §10 | API tests. |
| NFR-010 | Localization-ready: all UI strings via i18n keys (react-i18next, `en` bundle); backend user-facing messages from `messages.properties`; dates/numbers formatted by locale; UTF-8 throughout. | §10 | Lint rule / review; unit test of bundle completeness. |
| NFR-011 | Automated scheduled database backup (daily `pg_dump`, retention configurable, default 14 days) in the Compose bundle and a documented, tested restore procedure. | §7, §10, CL-F2 | Script + docs; manual restore drill documented. |
| NFR-012 | Responsive UI usable from 360 px width; supports current Chrome, Edge, Firefox, Safari. | §7, §10 | Playwright run at mobile & desktop viewport (Chromium). |
| NFR-013 | Money stored as `NUMERIC(15,2)`, computed with `BigDecimal`, HALF_UP to 2 dp; leave days `NUMERIC(6,2)`. | CL-B3 | Unit tests. |
| NFR-014 | Soft delete (`deleted_at`) for business master records (company, branch, department, lookups, employee, documents, shifts); historical transactions are never deleted. | §8 | Repository tests. |
| NFR-015 | Referential integrity via foreign keys; UUID primary keys generated by the application. | §8 | Migration review; tests. |
| NFR-016 | All list endpoints paginated (default 20, max 200) with indexed filter columns. | §10 | API tests. |
| NFR-017 | Optimistic locking (`version`) on mutable master/configuration entities; conflict → 409. | §10 | API test. |
| NFR-018 | Modular source code: one package per module with explicit public API (`api` services/DTOs); module dependency rules enforced by ArchUnit. | §10 | ArchUnit test. |
| NFR-019 | Security hardening: OWASP basics — input validation, parameterized queries only, CORS restricted to configured origins, security headers (CSP, X-Content-Type-Options, frame-ancestors none), rate limiting of login endpoint (≥ 20 req/min/IP → 429). | §10 | Tests for headers and rate limit. |
| NFR-020 | Automated test suites: backend unit + integration (`mvn verify`), frontend unit (`npm test`), E2E smoke (`npm run e2e`); all green before each commit. | §15 | CI commands. |
| NFR-021 | Deliverable docs: architecture (`specs/design.md`), API docs (OpenAPI), on-prem install guide, cloud deployment guide, backup & recovery procedure, release/update procedure (`docs/`), and an end-to-end user guide for administrators, HR, payroll, managers and employees (`docs/user-guide/`, Markdown + HTML with annotated screenshots). | §15 | Files exist and are reviewed; guide screenshots regenerated by the guide tour. |

---

## 12. Out of Scope (v1)

From §2.2: recruitment/ATS; performance appraisal & KPIs; learning management; expense claims; dedicated mobile app; AI workforce forecasting.

Deferred by CL-A1 (later SDD cycles):
- Full dashboard suite of §6 (Executive/HR/Attendance/Payroll/Shift dashboards with trends, document-expiry and probation analytics) beyond FR-096.
- Shift reports (coverage, staffing gaps, OT dependency) and trend reports.
- Scheduled/email delivery of reports.
- Licensing, shared-database multi-tenancy, centralized cloud administration console (roadmap Phase 6).
- SSO/OIDC, MFA.
- Additional DB vendors (interface only — NFR-005), additional statutory packs beyond `LK`, TimePro-specific adapter (no API spec provided).
- Effective-dated statutory rate history (rates apply to periods processed after the change).
- Non-monthly payroll frequencies, multi-currency per company, bank transfer file formats.
- Employee self-editing of profile data, leave encashment, compensatory-off.
- UAT checklist (§15). (The administrator / HR & payroll user manual is delivered as `docs/user-guide/` — T-041.)
