#!/bin/sh
# Restores a backup taken by backup.sh. Stop the app first (see docs/backup-recovery.md):
#   docker compose -f deploy/compose.onprem.yml stop app
#   docker compose -f deploy/compose.onprem.yml exec backup /scripts/restore.sh 20261007-020000
#   docker compose -f deploy/compose.onprem.yml start app
set -eu
ts=${1:?usage: restore.sh <timestamp>, e.g. 20261007-020000 (see ls /backups)}
db=/backups/hrm-db-$ts.dump
files=/backups/hrm-files-$ts.tar.gz
[ -f "$db" ] || { echo "missing $db" >&2; exit 1; }
[ -f "$files" ] || { echo "missing $files" >&2; exit 1; }
pg_restore --clean --if-exists --no-owner --single-transaction -d "$PGDATABASE" "$db"
find /files -mindepth 1 -delete
tar -xzf "$files" -C /files
echo "restored $ts"
