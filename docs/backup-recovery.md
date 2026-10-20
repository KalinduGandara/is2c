# Premium HRM — Backup and recovery

## What is backed up

| Data | Where it lives | How it is backed up |
|---|---|---|
| Database (all HR, attendance, leave and payroll data, and the audit log) | `db-data` volume | `pg_dump -Fc` (custom format, compressed) |
| Uploaded documents (employee documents, leave attachments) | `files` volume | `tar.gz` archive |
| Configuration and secrets | `deploy/.env` | **Not** included. Store it separately, for example in a password manager or secrets store. |
| HTTPS certificates | `caddy-data` volume | Not needed. Caddy re-issues certificates. |

A backup set is two files with the same timestamp in `deploy/backups/`:

```
hrm-db-20261007-020000.dump
hrm-files-20261007-020000.tar.gz
```

## Schedule and retention (NFR-011)

- The `backup` service runs `deploy/backup/backup.sh` every day at `HRM_BACKUP_AT` (default `02:00`) in the
  container time zone `TZ`.
- Sets older than `HRM_BACKUP_RETENTION_DAYS` (default 14) are deleted after each successful run.
- To check the schedule, run `docker compose -f deploy/compose.onprem.yml logs backup`. Every run logs either
  `backup <timestamp> done …` or `backup FAILED`.
- Copy `deploy/backups/` to another machine or to object storage every day. A backup kept only on the same
  disk does not protect against disk loss.

## Take a backup now

Do this before an upgrade or a risky change:

```bash
docker compose -f deploy/compose.onprem.yml exec backup /scripts/backup.sh
ls -lh deploy/backups/
```

## Restore

Restoring **replaces** the current database and documents with the chosen set.

1. Pick the timestamp to restore:
   ```bash
   ls deploy/backups/
   ```
2. Stop the application, but leave the database running:
   ```bash
   docker compose -f deploy/compose.onprem.yml stop app
   ```
3. Restore. The database restore runs in a single transaction, so a failure leaves the database untouched:
   ```bash
   docker compose -f deploy/compose.onprem.yml exec backup /scripts/restore.sh 20261007-020000
   ```
4. Start the application and verify:
   ```bash
   docker compose -f deploy/compose.onprem.yml start app
   docker compose -f deploy/compose.onprem.yml ps    # app: healthy
   ```
   Sign in and spot-check: the latest employees, the last payroll period and one document download.

### Restore onto a new server (disaster recovery)

1. Install the bundle as described in [install-onprem.md](install-onprem.md) using the **same** `deploy/.env`.
   The JWT secret may differ; users then simply sign in again.
2. Start only the database and the backup service:
   `docker compose -f deploy/compose.onprem.yml up -d db backup`.
3. Copy the backup set into `deploy/backups/`.
4. Run `restore.sh <timestamp>` as above.
5. Start the rest: `docker compose -f deploy/compose.onprem.yml up -d`.

## Restore drill

Run this drill after installation and at least once per quarter. Record the date and result.

1. Take a manual backup (see above).
2. Create a throwaway record, for example a holiday called `DRILL`.
3. Restore the backup taken in step 1.
4. Confirm that the `DRILL` holiday is gone and that the data from before the backup is present.

The drill was verified during release testing on 2026-10-07 with Docker 27 and PostgreSQL 17. A backup taken
via `backup.sh` was restored with `restore.sh`. The application came back healthy, sign-in worked, and data
created after the backup was gone.

## Windows installation

The Windows installer ([install-windows.md](install-windows.md)) produces the same backup sets with the same
file names and formats as Docker, so a set restores on either platform.

- **Schedule**: the scheduled task *Premium HRM backup* runs `scripts\backup.ps1` daily at `BackupAt` (default
  02:00, server time zone). It keeps `BackupRetentionDays` (default 14) and writes to `BackupDir` (default
  `<data folder>\backups`). These settings are in `<data folder>\config\install.ini`. Each run logs
  `backup <timestamp> done …` or `backup FAILED …` to `<data folder>\logs\backup.log`.
- **Upgrades**: the installer takes a backup before it upgrades, and asks before it continues if that backup
  fails.
- **Not included**: `<data folder>\config\`, which holds the secrets. Keep a copy in a password manager.
- **Manual backup**: run this in an elevated PowerShell:
  ```powershell
  & "C:\Program Files\Premium HRM\scripts\backup.ps1"
  ```
- **Restore**: this stops `HRM-App`, restores the database in a single transaction, replaces the documents,
  and then starts the app again. It asks you to type `RESTORE` first; pass `-Force` to skip that prompt.
  ```powershell
  & "C:\Program Files\Premium HRM\scripts\restore.ps1" -Timestamp 20261007-020000
  ```
- **Disaster recovery onto a new server**:
  1. Install Premium HRM.
  2. Stop the app with `Stop-Service HRM-App`.
  3. Copy the backup set into the backups folder.
  4. Run `restore.ps1`.

  Users sign in with the accounts from the backup.
