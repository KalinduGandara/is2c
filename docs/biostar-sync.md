# BioStar 2 → Premium HRM sync

A small separate application that copies **users** and **attendance punches** from a Suprema BioStar 2 server into
Premium HRM. It runs next to BioStar (or anywhere that can reach both servers), has a one-page web UI on
`http://localhost:8095`, and can sync automatically every few minutes.

Source: [`integrations/biostar-sync/`](../integrations/biostar-sync/).

## What it does

Every run does two steps, always in this order:

1. **Users → employees.** It reads all BioStar users. Users in groups that you mapped to an HRM department are created in
   HRM as employees if HRM does not have them yet. **Employee number and badge ID are the BioStar user ID**, which is
   what HRM uses to link punches to employees. The sync **never changes or deletes** existing employees: once an employee
   is in HRM, HR owns the record.
2. **Punches → attendance events.** It reads the successful identification/verification events (fingerprint, face, card,
   PIN, QR, mobile card…) in the time range and sends those of synced users to HRM's attendance ingestion API. HRM then
   computes daily attendance as for any other device.

What is **not** sent:

| Skipped | Why |
|---|---|
| Failed scans (e.g. `5124 IDENTIFY_FAIL_FINGERPRINT`), door, alarm and device events | Not attendance; failed scans have no user. |
| Users in groups set to "Do not sync" and disabled users, and their punches | Leavers ("Remove"), contractors, visitors. |
| Punches of users deleted from BioStar | They have no employee to belong to. |

### Punch direction (IN/OUT)

BioStar records which T&A key the person pressed (`tna_key`) only when the device asks for it. The keys are configured on
the device; by default the sync maps key `1` → IN and `2` → OUT (change this in the UI). Punches without a key, or with
another key, are sent as UNKNOWN, and HRM pairs them in time order (first IN, then OUT, …).

### Time zones

BioStar's event time is UTC and is passed to HRM unchanged. The dates you type in the UI are interpreted in the **HRM
company's time zone** (e.g. "2025-04-01 to 2025-04-30" in Asia/Colombo means 2025-03-31 18:30 UTC to
2025-04-30 18:29:59 UTC).

### Running twice is safe

Each punch is sent with a unique external id `<device id>:<device event index>`, and HRM skips punches it already has
(they appear as "already in HRM" in the history). You can re-sync any range at any time.

> BioStar's own event `id` is **not** unique: it restarts every month (it is the row number in a monthly table). It is
> therefore not used.

## Before you start

**In BioStar 2:** a login with permission to read users and events (e.g. the administrator, or an operator with
User and Monitoring read rights). Note the server address including the port, e.g. `https://192.168.1.10:444`.

**In Premium HRM** (as an administrator):

1. **Organisation:** the company with at least one branch, the departments you want to map BioStar groups to, a
   designation and an employment category to give new employees (they can be changed per employee later).
2. **Role:** Administration → Roles → new role, e.g. *BioStar sync*, with exactly these permissions:
   `EMPLOYEE:VIEW`, `EMPLOYEE:ADD`, `EMPLOYEE:CONFIGURE` (needed to set the employee number), `ORG:VIEW`,
   `ATTENDANCE:EDIT` (to relink earlier unmatched punches).
3. **Service account:** Administration → Users → new user, e.g. `biostar-sync`, with that role and data scope
   *Company* = the company. **Sign in once with it and set its permanent password** (new accounts must change their
   password; the sync cannot do that for you).
4. **Attendance source:** Attendance → Sources → new source in the company, adapter *Generic JSON*. Copy the API key
   (shown once).
5. Assign shifts (default shift, rotation or roster) as usual. Without a schedule, days show as *Unscheduled*.

## Install and run

Requires Java 21 (the same runtime as HRM).

```bash
cd integrations/biostar-sync
./mvnw -q package -DskipTests           # Windows: mvnw.cmd -q package -DskipTests
java -jar target/biostar-sync.jar
```

Open <http://localhost:8095>.

Settings and sync state are kept in `~/.biostar-sync/` (Windows: `C:\Users\<you>\.biostar-sync\`):
`settings.json` and `state.json`. **Passwords and the API key are stored there in plain text**; on Linux/macOS the
files are readable by their owner only. Run the application under a dedicated account and protect that folder.

Options (add to the `java -jar` command line):

| Option | Default | Meaning |
|---|---|---|
| `--server.port=8095` | 8095 | UI port |
| `--sync.data-dir=…` | `~/.biostar-sync` | Where settings and state are stored |
| `--server.address=127.0.0.1` | 127.0.0.1 | Network address the UI listens on |
| `--sync.allowed-hosts=…` | `localhost,127.0.0.1,[::1]` | Host names the UI answers to |

The UI has **no login** — anyone who can open it can see the configuration and start syncs. It therefore listens on
this machine only. To reach it from another machine, prefer an SSH tunnel; if you must expose it, set
`--server.address=0.0.0.0` **and** add the server's name to `--sync.allowed-hosts`, and restrict access with a firewall.

To keep it running after a restart, register it as a service: on Linux a systemd unit running `java -jar …`; on
Windows a scheduled task "At startup" or a service wrapper such as WinSW (as used by the HRM Windows installer).
Automatic sync only runs while the application runs.

## Set up (in the UI)

1. **BioStar 2 server** — URL, login ID, password. BioStar ships a self-signed certificate; tick *Trust the server's
   certificate without checking it* if the test says the certificate is not trusted (only on a network you trust).
   *Save and test* shows the number of BioStar users when the connection works.
2. **Premium HRM** — HRM URL (e.g. `https://hrm.example.com`), the service account, and the attendance source API key.
3. **New employees** — select the HRM company, then:
   - the branch, designation, employment category and join date that new employees get (BioStar has none of these);
   - for each BioStar user group, the HRM department, or *Do not sync*. The user count is shown per group. Leave
     leavers (e.g. a "Remove" group), contractors and visitors on *Do not sync*;
   - the `tna_key` values for IN and OUT.

   *Reload groups and HRM lists* fetches new groups/departments after you add them in BioStar or HRM.
4. **Sync** — choose a date range and press **Preview (no changes)** first: the history shows how many employees would
   be created and how many punches would be sent. Then press **Sync now**.

Names: BioStar has a single name field. The last word becomes the last name and the rest the first name; a one-word
name is used for both. Fix names in HRM if needed — later syncs do not overwrite them.

## Automatic sync

Tick *Sync automatically*, set the interval (minutes) and the date the first run starts from, and save.

- The first automatic run imports punches from the start date until now.
- Each later run continues from the latest punch already read, re-reading the last *N* hours (default 24). Devices that
  were offline upload their punches later with the original time; the look-back catches them. HRM skips punches it
  already has.
- If a device was offline for longer than the look-back, run a manual sync for those dates.
- *Reset position to start date* makes the next run start again from the start date.
- Each run also creates any new BioStar users of mapped groups, so new joiners appear in HRM by themselves.

A manual sync does not change the automatic position, and only one sync runs at a time.

## History and troubleshooting

The history lists the last 20 runs: employees created / already in HRM / not synced / failed, and punches new / already
in HRM / unmatched / rejected / not synced. *Details* shows per-row errors.

| Message | What to do |
|---|---|
| `TLS certificate not trusted` | Tick *Trust the server's certificate* (self-signed BioStar certificate). |
| `the HRM account '…' must change its password` | Sign in to HRM once with the service account and set a new password, then save it in the UI. |
| `HTTP 403 … EMPLOYEE:CONFIGURE` / other 403 | The service account's role misses one of the permissions listed above. |
| `HTTP 401` on punches | Wrong or regenerated API key, or the source was deactivated: paste the current key. |
| `HRM company not found or not visible` | The account's data scope does not include the selected company. |
| `user 1234 (…): … failed` | That employee could not be created (e.g. the department was deleted). Fix and sync again; the punches already sent are linked automatically once the employee exists. |
| Punches *unmatched* | HRM has no employee with that number/badge (e.g. it failed to be created). Fix the employee; the next run that creates employees relinks them, or use Attendance → Unmatched → Rematch in HRM. |
| *Not synced* is high | Expected for unmapped groups; check the group mapping. |

The application log (console) has the full error of a failed run.
