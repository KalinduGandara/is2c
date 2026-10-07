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
