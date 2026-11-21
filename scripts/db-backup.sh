#!/bin/sh
# Database backup with retention (MEDK-71). POSIX sh: runs on a host with the PostgreSQL 18 client
# or inside the postgres:18-alpine image (see docs/deployment/changes/MEDK-71.md).
#
#   1. pg_dump (custom format, compressed) to BACKUP_DIR/<prefix>-<UTC timestamp>.dump
#   2. Checks the dump can be read back (pg_restore --list) before keeping it.
#   3. Deletes dumps older than BACKUP_RETENTION_DAYS (default 30): data deleted from the database
#      (definitive clinic removal) is gone from every backup after that many days.
#
# Connection: standard libpq variables PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD (secret,
# from the environment's secret manager; never pass it as an argument). Never prints values.
# Exit code 0 only if the new dump is valid; the purge never runs after a failed dump.
set -eu

BACKUP_DIR="${BACKUP_DIR:?Set BACKUP_DIR (directory where the dumps are kept)}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
BACKUP_PREFIX="${BACKUP_PREFIX:-medical}"
: "${PGHOST:?Set PGHOST}"
: "${PGDATABASE:?Set PGDATABASE}"
: "${PGUSER:?Set PGUSER}"

case "$BACKUP_RETENTION_DAYS" in
  ''|*[!0-9]*) echo "BACKUP_RETENTION_DAYS must be a whole number of days" >&2; exit 2 ;;
esac
if [ "$BACKUP_RETENTION_DAYS" -lt 1 ]; then
  echo "BACKUP_RETENTION_DAYS must be at least 1" >&2
  exit 2
fi
case "$BACKUP_PREFIX" in
  ''|*[!A-Za-z0-9_-]*) echo "BACKUP_PREFIX may only contain letters, digits, '-' and '_'" >&2; exit 2 ;;
esac

umask 077
mkdir -p "$BACKUP_DIR"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_DIR/$BACKUP_PREFIX-$timestamp.dump"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

echo "Backup started: $(basename "$target")"
pg_dump --format=custom --compress=6 --no-owner --no-privileges --file="$partial"
pg_restore --list "$partial" > /dev/null
mv "$partial" "$target"
echo "Backup written: $(basename "$target") ($(wc -c < "$target" | tr -d ' ') bytes)"

# Retention: only this script's dumps (prefix and extension), never other files in the directory.
minutes=$((BACKUP_RETENTION_DAYS * 1440))
find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name "$BACKUP_PREFIX-*.dump" -o -name "$BACKUP_PREFIX-*.dump.partial" \) \
  -mmin +"$minutes" -print | while IFS= read -r old; do
    rm -f "$old"
    echo "Deleted by retention ($BACKUP_RETENTION_DAYS days): $(basename "$old")"
  done

kept="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name "$BACKUP_PREFIX-*.dump" | wc -l | tr -d ' ')"
echo "Backup finished: $kept dump(s) kept"
