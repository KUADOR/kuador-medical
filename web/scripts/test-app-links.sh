#!/bin/sh
# Tests docker-entrypoint.d/50-app-links.sh without Docker: it must keep the bundled files when
# no values are given, write valid JSON with the given values and reject malformed values.
# Run from web/: scripts/test-app-links.sh (needs node to parse JSON).
set -eu

SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/docker-entrypoint.d/50-app-links.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
FP_A="AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
FP_B="11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF"
failures=0

check() { # check <description> <node expression over j (parsed JSON)> <file>
  if node -e "const j = JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8')); if (!($2)) process.exit(1);" "$3"; then
    echo "PASS $1"
  else
    echo "FAIL $1"; failures=$((failures + 1))
  fi
}

run() { env -i PATH="$PATH" APP_LINKS_OUTPUT_DIR="$TMP/out" "$@" sh "$SCRIPT" >/dev/null 2>&1; }

# 1. No values: bundled files are kept untouched.
mkdir -p "$TMP/out"; cp public/.well-known/* "$TMP/out/"
run
check "bundled assetlinks.json kept" 'Array.isArray(j) && j.length === 0' "$TMP/out/assetlinks.json"
check "bundled apple-app-site-association kept" 'j.applinks.details.length === 0' "$TMP/out/apple-app-site-association"

# 2. With values: valid files with every fingerprint, app ID and link path.
run APP_LINKS_ANDROID_SHA256="$FP_A, $FP_B" APP_LINKS_IOS_APP_IDS="ABCDE12345.com.kuador.medicalKuador"
check "assetlinks.json written" 'j[0].target.package_name === "com.kuador.medical_kuador" && j[0].target.sha256_cert_fingerprints.length === 2 && j[0].relation[0] === "delegate_permission/common.handle_all_urls"' "$TMP/out/assetlinks.json"
check "apple-app-site-association written" 'j.applinks.details[0].appIDs[0] === "ABCDE12345.com.kuador.medicalKuador" && j.applinks.details[0].components.map(c => c["/"]).join() === "/invitation,/password-reset,/email-verification"' "$TMP/out/apple-app-site-association"

# 3. Malformed values are rejected (no JSON injection).
for bad in 'APP_LINKS_ANDROID_SHA256=AA:BB' 'APP_LINKS_ANDROID_SHA256='"$FP_A"'"]' 'APP_LINKS_IOS_APP_IDS=bad"id' \
           "APP_LINKS_ANDROID_PACKAGE=com.x\"y APP_LINKS_ANDROID_SHA256=$FP_A"; do
  # shellcheck disable=SC2086
  if run $bad; then echo "FAIL rejects $bad"; failures=$((failures + 1)); else echo "PASS rejects $bad"; fi
done

[ "$failures" -eq 0 ] || { echo "$failures check(s) failed"; exit 1; }
