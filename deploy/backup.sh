#!/bin/sh
# Runs inside the "backup" container (compose.prod.yaml). Dumps the database
# to /backups (deploy/backups on the server) every BACKUP_INTERVAL_SECONDS
# and deletes dumps older than BACKUP_KEEP_DAYS.
#
# Take one backup right now (e.g. before an upgrade):
#   docker compose -f compose.prod.yaml exec backup sh /scripts/backup.sh once
#
# Connection settings come from the standard PG* variables set in compose.
set -u

: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${BACKUP_KEEP_DAYS:=14}"

# Dumps contain password hashes and account data - owner-only files.
umask 077

backup_once() {
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    final="/backups/jwt_auth-$stamp.dump"
    # Write under a temporary name so a half-written dump (disk full, DB
    # restart) never looks like a good backup.
    partial="/backups/.jwt_auth-$stamp.dump.partial"
    if pg_dump --format=custom --file="$partial"; then
        mv "$partial" "$final"
        echo "backup ok: $final ($(du -h "$final" | cut -f1))"
    else
        rm -f "$partial"
        echo "backup FAILED at $stamp" >&2
        return 1
    fi
}

if [ "${1:-}" = "once" ]; then
    backup_once
    exit $?
fi

while true; do
    backup_once
    find /backups -name 'jwt_auth-*.dump' -mtime +"$BACKUP_KEEP_DAYS" -delete
    sleep "$BACKUP_INTERVAL_SECONDS"
done
