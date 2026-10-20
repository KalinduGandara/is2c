# Premium HRM — Windows installation

This guide installs Premium HRM on a single Windows server using `PremiumHRM-Setup-<version>.exe`. Docker is
not needed. The installer bundles everything and runs it as Windows services:

| Service | Name in *Services* | Purpose |
|---|---|---|
| `HRM-App` | Premium HRM | Spring Boot API + web UI, on `127.0.0.1:8080` only, as *Local Service* |
| `HRM-PostgreSQL` | HRM-PostgreSQL | PostgreSQL 17, on `localhost:5432` only, as *Network Service* |
| `HRM-Caddy` | Premium HRM HTTPS proxy | HTTPS on ports 443/80, with certificates from Caddy's internal CA, as *Local Service* |
| Scheduled task | Premium HRM backup | Daily `pg_dump` + archive of uploaded documents, at 02:00 |

The Linux/Docker installation is described in [install-onprem.md](install-onprem.md). Both installations use
the same application, configuration variables and backup format.

## 1. Requirements

- Windows Server 2019 or later, or Windows 10/11 (1809 or later), 64-bit.
- 4 vCPU, 8 GB RAM and 50 GB free disk. This sizing processes payroll for 1,000 employees in well under a
  minute.
- An administrator account to run the installer.
- A host name that users can reach, for example `hrm.local` in the company DNS, or a fixed IP address.
- Ports 80 and 443 free. The installer checks them, see [Port conflicts](#port-conflicts).
- PowerShell scripts allowed to run with `-ExecutionPolicy Bypass`. A Group Policy that enforces `AllSigned`
  blocks the installer's scripts.

## 2. Install

1. Run `PremiumHRM-Setup-<version>.exe` as administrator.
2. **Program folder**: the default is `C:\Program Files\Premium HRM`.
3. **Data folder**: the default is `C:\ProgramData\PremiumHRM`. It holds the configuration, database,
   documents, backups and logs, and only administrators can open it. Choose a local disk with enough space.
4. **Web address**: enter the host name users type in the browser, plus the HTTPS port (default 443) and the
   HTTP port (default 80, which redirects to HTTPS).
5. **First administrator**: enter a user name and an initial password. The password needs at least 10
   characters with upper- and lower-case letters, a digit and a symbol. The account must choose a new
   password at first sign-in.
6. Click **Install**. Setup then does the following:
   - Installs the Microsoft Visual C++ runtime if it is missing.
   - Creates the PostgreSQL cluster and the `hrm` database. The database passwords and the JWT secret are
     generated at random.
   - Registers and starts the services.
   - Waits until the app reports healthy. The first start applies the database migrations and takes up to a
     few minutes.
   - Trusts the HTTPS root certificate on this server.
   - Adds a firewall rule for the two web ports and schedules the daily backup.
7. Leave **Open Premium HRM in the browser** ticked and click **Finish**.

The setup log is `<data folder>\logs\setup.log`. If configuration fails, the installer says so. Fix the cause
and run the installer again: it reuses what was already done.

### Port conflicts

The installer checks for port conflicts before it changes anything:

- **Web ports (HTTPS/HTTP)**: when a port is taken, the wizard names the program or service that holds it and
  does not continue. Ports 80/443 are often held by IIS ("World Wide Web Publishing Service"). Ports can also
  fall in a range Windows reserves for Hyper-V, WSL or Docker. Stop or reconfigure that program, or enter
  other ports. With a non-standard HTTPS port, users open `https://<host>:<port>/`.
- **Internal ports**: the app (8080) and PostgreSQL (5432) listen on this server only. If either port is
  taken, for example by an existing PostgreSQL installation, setup uses the next free port and records it in
  `setup.log` and `config\install.ini`. An existing PostgreSQL is left untouched.

## 3. Trust the HTTPS certificate on client PCs

Caddy issues the site certificate from its own internal CA. Setup trusts that CA on the server itself and
copies its root certificate to `<data folder>\hrm-root.crt`.

Distribute `hrm-root.crt` to client PCs as a *Trusted Root Certification Authority*, for example through Group
Policy (*Computer Configuration → Policies → Windows Settings → Security Settings → Public Key Policies →
Trusted Root Certification Authorities*). Until a PC trusts it, its browser shows a security warning.

To use a certificate from your company CA instead:

1. Put `hrm.crt` and `hrm.key` in `<data folder>\caddy\`.
2. In `<data folder>\caddy\Caddyfile`, replace `tls internal` with `tls hrm.crt hrm.key`, giving the full
   paths with forward slashes.
3. Run `Restart-Service HRM-Caddy`.

Setup regenerates the Caddyfile on every upgrade, so repeat step 2 after each upgrade.

## 4. First login

Open `https://<host>/` and continue with step 6 of [install-onprem.md](install-onprem.md#6-first-login): sign
in, change the password, create the company, add users and register the attendance devices. The Start menu
has shortcuts to the app, the user guide and the data folder.

## 5. Configuration

| File | Contents |
|---|---|
| `<data folder>\config\hrm.properties` | App settings. Uses the same `HRM_*` variables as [`deploy/.env.example`](../deploy/.env.example). |
| `<data folder>\config\install.ini` | Installer settings: host name, ports, backup time and retention, `BackupDir`, `JavaOptions`. |
| `<data folder>\config\postgres.properties` | Password of the PostgreSQL superuser `postgres`, for maintenance only. |

- **Email**: in `hrm.properties`, set `HRM_MAIL_ENABLED=true` and the `HRM_SMTP_*` lines, then run
  `Restart-Service HRM-App`.
- **Backups**: in `install.ini`, change `BackupRetentionDays` or `BackupDir`. To use another disk, set
  `BackupDir=D:\HRM-backups`. The backup task runs as SYSTEM, so a network share must grant write access to
  the server's computer account. To change `BackupAt`, `JavaOptions` (default `-XX:MaxRAMPercentage=25`) or
  the host name, edit `install.ini`, then run the installer again; it rewrites the services and the
  Caddyfile from that file.
- **Time zone**: the app uses the server's Windows time zone. Set it before installing, for example
  `Set-TimeZone -Id 'Sri Lanka Standard Time'`.

> [!WARNING]
> Keep a copy of the `config` folder in a password manager or vault. Backups do not include it, and restoring
> on a new server needs at least `HRM_DB_PASSWORD`. If the JWT secret is lost, a new one is generated and users
> simply sign in again.

## 6. Operations

Run these commands in an elevated PowerShell:

| Task | Command |
|---|---|
| Status | `Get-Service HRM-*` |
| Stop / start everything | `Stop-Service HRM-Caddy,HRM-App,HRM-PostgreSQL` / `Start-Service HRM-PostgreSQL,HRM-App,HRM-Caddy` |
| Restart the app | `Restart-Service HRM-App` |
| Logs | `<data folder>\logs\`: `hrm-app.out.log` (JSON), `hrm-app.err.log`, `caddy.log`, `setup.log`, `backup.log`. PostgreSQL logs are in `pgdata\log\`. |
| Manual backup | `& "C:\Program Files\Premium HRM\scripts\backup.ps1"` |
| Restore | See [backup-recovery.md](backup-recovery.md#windows-installation) |
| Upgrade | Run the new `PremiumHRM-Setup-<version>.exe`, see [release-update.md](release-update.md#windows-installation) |

## 7. Uninstall

Use *Settings → Apps → Premium HRM → Uninstall*. The uninstaller removes:

- the services, the backup task and the firewall rule
- the trusted root certificate
- the program files

It then asks whether to delete the data folder, and the default is **No**. If you keep the data, installing
Premium HRM again later with the same data folder reuses the database, configuration and backups.

## 8. Moving from Docker to Windows

1. Take a backup on the Docker installation: `docker compose ... exec backup /scripts/backup.sh`.
2. Install on Windows as above.
3. Copy the two files of the backup set into `<data folder>\backups\`.
4. Run `restore.ps1 -Timestamp <timestamp>`. The backup formats are the same.

Users then sign in with their existing accounts. The first administrator from the Windows installer is not
created, because the database already has users.
