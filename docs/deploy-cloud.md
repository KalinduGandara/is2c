# Premium HRM — Cloud deployment

The cloud edition uses the same container image as on-prem (NFR-001). Each customer gets their own deployment:
their own VM or project, their own database and their own domain. Differences from on-prem:

- `HRM_EDITION=CLOUD`.
- Caddy obtains a public Let's Encrypt certificate for `HRM_DOMAIN` and sends HSTS (NFR-002).
- No root certificate needs to be distributed to clients.

## 1. Provision

- A VM with 4 vCPU, 8 GB RAM and 50 GB SSD, running Docker Engine 24+ with Compose. A managed container host
  that runs Compose also works.
- A DNS `A`/`AAAA` record for the customer's domain (for example `acme.hrm.example.com`) pointing at the VM.
- Inbound TCP 80 and 443 open to the internet. Port 80 is needed for the ACME HTTP challenge and redirects.
- Outbound access to Let's Encrypt and to the SMTP relay.

## 2. Configure

```bash
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
```

Set the same required values as on-prem (`HRM_DB_PASSWORD`, `HRM_JWT_SECRET`, `HRM_ADMIN_*`), plus:

| Variable | Value |
|---|---|
| `HRM_DOMAIN` | The public DNS name, e.g. `acme.hrm.example.com` |
| `HRM_ACME_EMAIL` | Contact address for certificate expiry notices |
| `HRM_IMAGE` | The released image tag, e.g. `registry.example.com/is2c/hrm:1.0.0` |
| `HRM_MAIL_ENABLED=true` and `HRM_SMTP_*` | Your SMTP relay (recommended in the cloud) |

Store `deploy/.env` in your secrets manager. Do not keep it in source control.

## 3. Deploy

```bash
docker compose -f deploy/compose.cloud.yml pull
docker compose -f deploy/compose.cloud.yml up -d
docker compose -f deploy/compose.cloud.yml ps        # app becomes healthy in about a minute
```

Then open `https://<HRM_DOMAIN>/`, sign in as the bootstrap administrator and continue as in
[install-onprem.md §6](install-onprem.md#6-first-login).

## 4. Backups in the cloud

The `backup` service writes daily dumps to `deploy/backups/` on the VM. In the cloud you must also copy them
off the VM. For example, sync that directory to object storage with a cron job:

```bash
# /etc/cron.d/hrm-offsite: every day at 03:00
0 3 * * * root aws s3 sync /opt/hrm/deploy/backups s3://acme-hrm-backups/ --storage-class STANDARD_IA
```

Also enable VM disk snapshots as a second layer. Restore steps are in
[backup-recovery.md](backup-recovery.md).

## 5. Monitoring

- **Liveness/health:** `https://<HRM_DOMAIN>/actuator/health` returns `{"status":"UP"}`.
- **Logs:** the app writes structured ECS JSON to stdout. Ship them with your log agent, for example the
  Docker `json-file` driver plus a collector. Each request carries an `X-Correlation-Id` that also appears in
  error responses.
- **Alerts:** the health endpoint is not UP, a `backup FAILED` line appears in the `backup` logs, or disk
  usage exceeds 80%.

## 6. Hardening checklist

- Keep `HRM_API_DOCS=false`, unless you need the docs temporarily.
- Restrict SSH to the VM. Only ports 80 and 443 should be public; PostgreSQL is never published.
- Rotate `HRM_JWT_SECRET` only during a maintenance window, because rotating it signs everyone out.
- Apply OS and image updates through the procedure in [release-update.md](release-update.md).
