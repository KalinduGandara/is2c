# biostar-sync

Standalone middleware that syncs Suprema **BioStar 2** users and attendance punches into **Premium HRM**
(FR-045/FR-047: device integration through the generic JSON ingestion API). Operator guide:
[`docs/biostar-sync.md`](../../docs/biostar-sync.md).

Spring Boot 4.1 / Java 21, one Thymeleaf page, JDK `HttpClient`; no database (settings and state are JSON files).
Independent of the HRM build: it only talks to HRM's public REST API.

```bash
./mvnw -q test                     # unit + fake-server sync tests
./mvnw -q package -DskipTests      # → target/biostar-sync.jar
java -jar target/biostar-sync.jar  # UI on http://localhost:8095
```

## How it maps

| BioStar 2 | Premium HRM |
|---|---|
| `POST /api/login` → `bs-session-id` header | `POST /api/v1/auth/login` (service account JWT) |
| `GET /api/users` (`user_id`, `name`, `user_group_id`) | `POST /api/v1/employees` — employee number = badge = `user_id`; department from the group mapping; branch, designation, category, join date from UI defaults. Existing numbers are never updated. |
| `POST /api/events/search` — `datetime` BETWEEN range, `event_type_id` IN success codes, ordered by `datetime`, paged by `offset` | `POST /api/v1/integrations/attendance/events` (`X-Api-Key`), batches of ≤1,000 |
| event `device_id.id` + `index` | `externalId` = `device:index` (BioStar `id` restarts monthly — not unique) |
| `datetime` (UTC) | `timestamp` unchanged |
| `tna_key` (configurable, default 1/2) | `direction` IN / OUT, else UNKNOWN |
| — | `POST /api/v1/attendance/rematch` after new employees were created |

Facts verified against a live BioStar 2 server (2026-10): `datetime` is real UTC; `server_datetime` cannot be filtered
on; `id` repeats every month while `(device_id, index)` is unique; offset paging over a fixed `datetime` range returns
exactly the rows of one unpaged call; `5124` is `IDENTIFY_FAIL_FINGERPRINT` (no user).

## Code

| Class | Role |
|---|---|
| `SyncService` | One run: users then events; manual range or automatic continuation (watermark = latest punch time, minus look-back); single-run guard; scheduler tick |
| `BiostarClient`, `HrmClient`, `Http` | REST clients (opt-in trust-all TLS for BioStar's self-signed certificate) |
| `Mapping` | Pure BioStar → HRM conversions |
| `Settings`, `SettingsStore`, `SyncReport` | Configuration, state and run history as owner-only JSON files |
| `WebController`, `templates/index.html` | The UI |
| `HostGuardFilter` | The UI has no login: refuses unknown `Host` names (DNS rebinding) and cross-site form posts |
