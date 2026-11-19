#!/bin/sh
# Writes the Android App Links and iOS Universal Links association files at container start,
# so one image serves every environment (ADR-014). Public values only: package name, signing
# certificate fingerprints and Apple app IDs are not secrets.
#   APP_LINKS_ANDROID_PACKAGE        Android applicationId (default com.kuador.medical_kuador)
#   APP_LINKS_ANDROID_SHA256         comma-separated SHA-256 fingerprints of the signing certificates
#   APP_LINKS_IOS_APP_IDS            comma-separated <TeamID>.<bundle id>
# Without fingerprints / app IDs the bundled files (no associated apps) are kept.
set -eu

# APP_LINKS_OUTPUT_DIR only exists for web/scripts/test-app-links.sh.
WELL_KNOWN="${APP_LINKS_OUTPUT_DIR:-/usr/share/nginx/html/.well-known}"
ANDROID_PACKAGE="${APP_LINKS_ANDROID_PACKAGE:-com.kuador.medical_kuador}"
ANDROID_SHA256="${APP_LINKS_ANDROID_SHA256:-}"
IOS_APP_IDS="${APP_LINKS_IOS_APP_IDS:-}"
# Must match kuador.links.*-path in api/src/main/resources/application.yaml.
LINK_PATHS="/invitation /password-reset /email-verification"

fail() { echo "50-app-links: $1" >&2; exit 1; }

# Prints a comma-separated list as JSON strings, validating each item against a regex.
json_list() {
  list="$1"; pattern="$2"; name="$3"; out=""
  old_ifs="$IFS"; IFS=','
  for item in $list; do
    item=$(printf '%s' "$item" | tr -d ' ')
    [ -z "$item" ] && continue
    printf '%s' "$item" | grep -Eq "$pattern" || { IFS="$old_ifs"; fail "invalid value in $name"; }
    out="${out:+$out, }\"$item\""
  done
  IFS="$old_ifs"
  printf '%s' "$out"
}

mkdir -p "$WELL_KNOWN"

if [ -n "$ANDROID_SHA256" ]; then
  printf '%s' "$ANDROID_PACKAGE" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$' \
    || fail "invalid APP_LINKS_ANDROID_PACKAGE"
  fingerprints=$(json_list "$ANDROID_SHA256" '^([0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$' APP_LINKS_ANDROID_SHA256)
  cat > "$WELL_KNOWN/assetlinks.json" <<JSON
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$ANDROID_PACKAGE",
      "sha256_cert_fingerprints": [$fingerprints]
    }
  }
]
JSON
  echo "50-app-links: assetlinks.json written"
fi

if [ -n "$IOS_APP_IDS" ]; then
  app_ids=$(json_list "$IOS_APP_IDS" '^[A-Z0-9]{10}\.[A-Za-z0-9.-]+$' APP_LINKS_IOS_APP_IDS)
  components=""
  for path in $LINK_PATHS; do
    components="${components:+$components, }{ \"/\": \"$path\" }"
  done
  cat > "$WELL_KNOWN/apple-app-site-association" <<JSON
{
  "applinks": {
    "details": [
      {
        "appIDs": [$app_ids],
        "components": [$components]
      }
    ]
  }
}
JSON
  echo "50-app-links: apple-app-site-association written"
fi
