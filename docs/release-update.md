# Premium HRM — Release and update procedure

## Versioning

- Releases use semantic versioning: `MAJOR.MINOR.PATCH`. The image is tagged `is2c/hrm:<version>` and the Git
  tag is `v<version>`.
- Database changes ship only as new Flyway migrations
  (`backend/src/main/resources/db/migration/postgresql/V<n>__*.sql`). Released migrations are never edited.
  The app validates the schema at start-up and refuses to run against an unknown schema.
- Migrations are forward-only. To roll back, restore the backup taken before the update (see below).

## Building a release (vendor side)

1. All checks must be green:
   ```bash
   (cd backend && ./mvnw verify && ./mvnw verify -Pperf)
   (cd frontend && ./check.sh && npm run e2e)
   ```
2. Set the version in `backend/pom.xml` and `frontend/package.json`, then commit and tag:
   `git tag v1.2.0 && git push --tags`.
3. Build and push the image from the repository root:
   ```bash
   docker build -f deploy/Dockerfile -t registry.example.com/is2c/hrm:1.2.0 .
   docker push registry.example.com/is2c/hrm:1.2.0
   ```
4. Write release notes covering new features, migrations included, configuration changes (new `HRM_*`
   variables) and any manual steps.
5. For offline on-prem customers, export the image:
   `docker save registry.example.com/is2c/hrm:1.2.0 | gzip > hrm-1.2.0.tar.gz`. Ship it with the `deploy/` folder.
6. Build the Windows installer on a Windows x64 machine with JDK 21, Node.js 22 and Inno Setup 6.3+:
   ```powershell
   powershell -ExecutionPolicy Bypass -File deploy\windows\build.ps1
   ```
   The output is `deploy\windows\build\out\PremiumHRM-Setup-1.2.0.exe`.
   - The script bundles the Temurin 21 JRE, the PostgreSQL Windows binaries (`-PostgresVersion`, default
     `17.6-1`), Caddy and WinSW.
   - Keep PostgreSQL on major version 17. Customer databases are not migrated across major versions.
   - Sign the `.exe` with the company code-signing certificate. Unsigned installers trigger a Windows
     SmartScreen warning. Signing can be done with `signtool sign` after the build, or with Inno Setup's
     `SignTool` setting.

## Updating an installation (customer side)

Plan a short maintenance window. Payroll processing should not be running during the update.

1. Read the release notes and add any new variables to `deploy/.env`.
2. Take a backup:
   ```bash
   docker compose -f deploy/compose.onprem.yml exec backup /scripts/backup.sh
   ```
3. Get the new image:
   - With registry access, set `HRM_IMAGE=registry.example.com/is2c/hrm:1.2.0` in `deploy/.env`, then run
     `docker compose -f deploy/compose.onprem.yml pull app`.
   - Offline, run `docker load < hrm-1.2.0.tar.gz`, then set `HRM_IMAGE` as above.
4. Replace the `deploy/` files (Compose files, Caddyfiles, backup scripts) with those from the release. Keep
   your `deploy/.env` and `deploy/backups/`.
5. Recreate the containers:
   ```bash
   docker compose -f deploy/compose.onprem.yml up -d
   ```
   Flyway applies new migrations automatically when `app` starts. Follow progress with `logs -f app`.
6. Verify:
   - `docker compose ... ps` shows `app` as `healthy`.
   - Sign in.
   - **Administration → Audit log** shows recent entries.
   - Open the latest payroll period.

Use `compose.cloud.yml` instead of `compose.onprem.yml` for cloud deployments.

## Rolling back

1. Set `HRM_IMAGE` back to the previous version.
2. Restore the backup taken in step 2, following [backup-recovery.md](backup-recovery.md#restore). This is
   required when the new version applied migrations, because older versions refuse a newer schema.
3. Run `docker compose -f deploy/compose.onprem.yml up -d`.

## Windows installation

Updating:

1. Read the release notes.
2. Run the new `PremiumHRM-Setup-<version>.exe` as administrator. The installer:
   - Skips the wizard pages. It keeps the program folder, data folder, ports, secrets and accounts.
   - Takes a backup with `backup.ps1`. If the backup fails, it asks before it continues.
   - Stops the services and replaces the program files, JRE and PostgreSQL binaries (same major version).
   - Starts everything again and waits until the app is healthy. Flyway applies new migrations on start.
3. If the release notes list new `HRM_*` variables, add them to `<data folder>\config\hrm.properties`, then run
   `Restart-Service HRM-App`.
4. Verify as above: sign in, check the audit log, and open the latest payroll period.

Rolling back:

1. Reinstall the previous `PremiumHRM-Setup-<version>.exe` over the current one.
   If the new version applied migrations, setup reports that the app did not become healthy. This is
   expected, because older versions refuse a newer schema.
2. Restore the backup the upgrade took before it changed anything:
   `restore.ps1 -Timestamp <timestamp>`. See [backup-recovery.md](backup-recovery.md#windows-installation).

**PostgreSQL major upgrades (for example 17 → 18)** are not done by the installer. If one is ever needed:

1. Take a backup.
2. Uninstall, choosing to delete the data. Keep a copy of the backup set and the `config` folder.
3. Install the new version.
4. Restore the backup.
