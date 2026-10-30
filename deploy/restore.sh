#!/bin/sh
# Replaces the live database with a backup made by backup.sh.
#   ./restore.sh backups/jwt_auth-20261030T020000Z.dump
#
# Run on the server from this directory. It stops the API, saves a safety
# backup of the current database, recreates the database from the dump, and
# starts the API again.
set -eu

cd "$(dirname "$0")"
compose() { docker compose -f compose.prod.yaml "$@"; }

dump="${1:-}"
if [ -z "$dump" ] || [ ! -f "$dump" ]; then
    echo "usage: $0 backups/jwt_auth-<timestamp>.dump" >&2
    exit 1
fi

echo "This REPLACES all data in the live database with: $dump"
printf 'Type "restore" to continue: '
read -r answer
[ "$answer" = "restore" ] || { echo "aborted"; exit 1; }

echo "-> stopping security-api"
compose stop security-api

echo "-> safety backup of the current database"
compose exec -T backup sh /scripts/backup.sh once

echo "-> recreating database from $dump"
compose exec -T postgres dropdb -U postgres --if-exists --force jwt_auth
compose exec -T postgres createdb -U postgres jwt_auth
compose exec -T postgres pg_restore -U postgres -d jwt_auth --no-owner --exit-on-error < "$dump"

echo "-> starting security-api"
compose start security-api
echo "done - check: docker compose -f compose.prod.yaml ps"
