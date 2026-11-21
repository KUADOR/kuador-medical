#!/usr/bin/env bash
# Test of scripts/db-backup.sh (MEDK-71) against a throwaway PostgreSQL 18 container (needs Docker).
# Checks: a valid dump is written and restores, dumps older than the retention are deleted, recent
# ones and unrelated files are kept, and a failed dump keeps every previous backup.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="postgres:18-alpine"
NAME="medk-backup-test-$$"
PASSWORD="$(openssl rand -hex 16)"
failures=0

cleanup() { docker rm -f "$NAME" > /dev/null 2>&1 || true; }
trap cleanup EXIT

check() {
  if eval "$2"; then echo "PASS $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

in_db() { docker exec -e PGPASSWORD="$PASSWORD" "$NAME" "$@"; }

docker run -d --name "$NAME" -e POSTGRES_DB=medical -e POSTGRES_USER=medical \
  -e POSTGRES_PASSWORD="$PASSWORD" "$IMAGE" > /dev/null
for _ in $(seq 1 60); do
  in_db pg_isready -h 127.0.0.1 -U medical -d medical > /dev/null 2>&1 && break
  sleep 1
done
docker cp "$ROOT/scripts/db-backup.sh" "$NAME:/usr/local/bin/db-backup.sh" > /dev/null

in_db psql -q -h 127.0.0.1 -U medical -d medical \
  -c "create table sample(id int primary key, note text); insert into sample values (1, 'kept');"

# Previous backups: one older than 30 days, one recent, plus a file the purge must not touch.
in_db sh -c 'mkdir -p /backups &&
  touch -d "@$(( $(date +%s) - 31 * 86400 ))" /backups/medical-20000101T000000Z.dump &&
  touch -d "@$(( $(date +%s) - 29 * 86400 ))" /backups/medical-20000102T000000Z.dump &&
  touch -d "@$(( $(date +%s) - 90 * 86400 ))" /backups/notes.txt'

run_backup() {
  in_db env PGHOST=127.0.0.1 PGDATABASE="$1" PGUSER=medical BACKUP_DIR=/backups sh /usr/local/bin/db-backup.sh
}

output="$(run_backup medical)"
check "backup ends with exit code 0" "true"
check "output never shows the password" "[[ \"\$output\" != *\"$PASSWORD\"* ]]"
check "dump older than 30 days is deleted" "! in_db test -e /backups/medical-20000101T000000Z.dump"
check "dump of 29 days is kept" "in_db test -e /backups/medical-20000102T000000Z.dump"
check "unrelated file is kept" "in_db test -e /backups/notes.txt"
new_dump="$(in_db sh -c 'ls -t /backups/medical-*.dump | head -1')"
check "new dump has owner-only permissions" "[[ \$(in_db stat -c %a \"$new_dump\") == 600 ]]"
check "no partial file is left" "[[ -z \$(in_db sh -c 'ls /backups/*.partial 2>/dev/null') ]]"

in_db createdb -h 127.0.0.1 -U medical restored
in_db pg_restore -h 127.0.0.1 -U medical -d restored --no-owner "$new_dump"
restored="$(in_db psql -tA -h 127.0.0.1 -U medical -d restored -c 'select note from sample where id = 1')"
check "dump restores the data" "[[ \"$restored\" == kept ]]"

before="$(in_db sh -c 'ls /backups | wc -l')"
in_db sh -c 'touch -d "@$(( $(date +%s) - 40 * 86400 ))" /backups/medical-20000103T000000Z.dump'
if run_backup does_not_exist > /dev/null 2>&1; then failed_ok=false; else failed_ok=true; fi
check "failed dump returns a non-zero exit code" "$failed_ok"
check "failed dump purges nothing" "in_db test -e /backups/medical-20000103T000000Z.dump"
check "failed dump leaves no new file" "[[ \$(in_db sh -c 'ls /backups | wc -l') -eq $((before + 1)) ]]"

check "invalid retention is rejected" \
  "! in_db env PGHOST=x PGDATABASE=x PGUSER=x BACKUP_DIR=/tmp/b BACKUP_RETENTION_DAYS=0 sh /usr/local/bin/db-backup.sh > /dev/null 2>&1"

if [[ "$failures" -gt 0 ]]; then echo "$failures check(s) failed"; exit 1; fi
echo "All checks passed"
