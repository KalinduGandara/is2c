# Premium HRM — On-premises installation

This guide installs Premium HRM on a single server inside a customer's network. You get one installation per
customer, with any number of companies inside it. The bundle runs four containers:

| Service | Purpose |
|---|---|
| `app` | Premium HRM (Spring Boot API + web UI), port 8080 inside the network only |
| `db` | PostgreSQL 17, data in the `db-data` volume |
| `caddy` | HTTPS reverse proxy on ports 80/443, certificates from Caddy's internal CA |
| `backup` | Daily `pg_dump` + archive of uploaded documents (see [backup-recovery.md](backup-recovery.md)) |

## 1. Requirements

- Linux server (x86-64 or arm64) with 4 vCPU, 8 GB RAM and 50 GB disk. This sizing processes payroll for
  1,000 employees in well under a minute.
- Docker Engine 24+ with the Compose plugin (`docker compose version`).
- A host name that users can reach, for example `hrm.local` in the company DNS, or a fixed IP address.
- Ports 80 and 443 free on the server.
- Optional: an SMTP server, used to email payslips and approval notifications.

## 2. Get the bundle

Copy the release bundle (the repository or the release archive) to the server, for example to `/opt/hrm`.
Then go to that directory:

```bash
cd /opt/hrm
```

## 3. Configure

```bash
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
```

Edit `deploy/.env`:

| Variable | What to set |
|---|---|
| `HRM_DB_PASSWORD` | A long random password. Generate one with `openssl rand -base64 24`. |
| `HRM_JWT_SECRET` | At least 32 random bytes. Generate it with `openssl rand -base64 48`. The app refuses to start without it. |
| `HRM_ADMIN_USERNAME` / `HRM_ADMIN_PASSWORD` | The first administrator. This account is created only when the database has no users, and it must change its password at first login. |
| `HRM_HOSTNAME` | The name users type in the browser, for example `hrm.local`. |
| `HRM_MAIL_*`, `HRM_SMTP_*` | Fill these in only if payslips and notifications should be emailed. |
| `TZ`, `HRM_BACKUP_AT`, `HRM_BACKUP_RETENTION_DAYS` | The backup schedule. The default is 02:00 Asia/Colombo, keeping 14 days. |

## 4. Start

To build the image on the server:

```bash
docker compose -f deploy/compose.onprem.yml up -d --build
```

If you received a prebuilt image, set `HRM_IMAGE` in `deploy/.env` and run `docker compose ... up -d`
without `--build`.

Check that everything is running. `app` should report `healthy` after about a minute:

```bash
docker compose -f deploy/compose.onprem.yml ps
docker compose -f deploy/compose.onprem.yml logs -f app
```

## 5. Trust the HTTPS certificate on client PCs

Caddy issues the site certificate from its own internal CA. Browsers show a warning until that CA's root
certificate is trusted. Export the root certificate:

```bash
docker compose -f deploy/compose.onprem.yml cp caddy:/data/caddy/pki/authorities/local/root.crt ./hrm-root.crt
```

Distribute `hrm-root.crt` to client PCs through your usual tooling, such as Group Policy, MDM or the macOS
keychain, as a trusted root CA.

If you have a certificate from the company CA, mount it into the `caddy` container instead. In
`deploy/Caddyfile.onprem`, replace `tls internal` with `tls /certs/hrm.crt /certs/hrm.key`.

## 6. First login

1. Open `https://<HRM_HOSTNAME>/` and sign in with `HRM_ADMIN_USERNAME` / `HRM_ADMIN_PASSWORD`.
2. Change the password when prompted.
3. Go to **Organization Setup** and create the company. Its settings, leave types, approval workflows and the
   Sri Lanka statutory payroll pack are created automatically.
4. Under **Administration**, create user accounts and assign roles and data scope.
5. Under **Attendance → Import & devices**, register each attendance device or middleware as a source. The API key
   is shown only once. Devices post events to `https://<HRM_HOSTNAME>/api/v1/integrations/attendance/events`
   with the header `X-Api-Key`.

For the full setup and day-to-day use, see the user guide: `docs/user-guide/index.html` (or `USER_GUIDE.md`).

## 7. Operations

| Task | Command |
|---|---|
| Stop / start | `docker compose -f deploy/compose.onprem.yml stop` / `start` |
| Logs (JSON) | `docker compose -f deploy/compose.onprem.yml logs --since 1h app` |
| Manual backup | `docker compose -f deploy/compose.onprem.yml exec backup /scripts/backup.sh` |
| API documentation | Set `HRM_API_DOCS=true`, restart `app`, then open `https://<host>/api/docs/ui` |
| Upgrade | See [release-update.md](release-update.md) |

Data lives in these Docker volumes:

- `hrm_db-data` (database)
- `hrm_files` (uploaded documents)
- `hrm_caddy-data` (certificates)

Backups are written to `deploy/backups/`. Copy that directory off the server regularly.
