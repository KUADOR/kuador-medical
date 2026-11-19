#!/bin/sh
# Writes config.json from API_BASE_URL at container start, so one image serves every environment.
# Without API_BASE_URL the bundled config.json (same-origin /api/v1, proxied by nginx) is kept.
set -eu

CONFIG_FILE=/usr/share/nginx/html/config.json

if [ -n "${API_BASE_URL:-}" ]; then
  case "$API_BASE_URL" in
    *\"* | *\\*) echo "40-runtime-config: API_BASE_URL contains invalid characters" >&2; exit 1 ;;
  esac
  printf '{\n  "API_BASE_URL": "%s"\n}\n' "$API_BASE_URL" > "$CONFIG_FILE"
  echo "40-runtime-config: config.json written from API_BASE_URL"
fi
