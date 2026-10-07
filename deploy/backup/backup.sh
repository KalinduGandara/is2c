#!/bin/sh
# One backup: PostgreSQL custom-format dump + archive of uploaded files, then prune old backups (NFR-011).
# Runs inside the `backup` service:  docker compose -f deploy/compose.onprem.yml exec backup /scripts/backup.sh
set -eu
dir=/backups
retention=${HRM_BACKUP_RETENTION_DAYS:-14}
ts=$(date +%Y%m%d-%H%M%S)
mkdir -p "$dir"
pg_dump -Fc --no-owner -f "$dir/hrm-db-$ts.dump.partial"
mv "$dir/hrm-db-$ts.dump.partial" "$dir/hrm-db-$ts.dump"
tar -czf "$dir/hrm-files-$ts.tar.gz.partial" -C /files .
mv "$dir/hrm-files-$ts.tar.gz.partial" "$dir/hrm-files-$ts.tar.gz"
find "$dir" -name 'hrm-*' -type f -mtime +"$retention" -delete
echo "backup $ts done: $(du -h "$dir/hrm-db-$ts.dump" | cut -f1) database, $(du -h "$dir/hrm-files-$ts.tar.gz" | cut -f1) files"
