#!/bin/sh
# Runs backup.sh every day at HRM_BACKUP_AT (HH:MM, container TZ).
set -eu
at=${HRM_BACKUP_AT:-02:00}
target=$(( $(echo "$at" | cut -d: -f1 | sed 's/^0//') * 3600 + $(echo "$at" | cut -d: -f2 | sed 's/^0//') * 60 ))
echo "daily backups at $at, retention ${HRM_BACKUP_RETENTION_DAYS:-14} days"
while true; do
    now=$(( $(date +%H | sed 's/^0//') * 3600 + $(date +%M | sed 's/^0//') * 60 + $(date +%S | sed 's/^0//') ))
    wait=$(( (target - now + 86400) % 86400 ))
    [ "$wait" -eq 0 ] && wait=86400
    sleep "$wait"
    /bin/sh /scripts/backup.sh || echo "backup FAILED" >&2
done
