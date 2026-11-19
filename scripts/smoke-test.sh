#!/usr/bin/env bash
# End-to-end smoke test of the Medical Kuador skeleton against a running api.
#
# Walks the identity flow through the HTTP API (and, optionally, through the web
# container's same-origin proxy): system info, clinic self-signup, login, role CRUD,
# cross-tenant isolation (404), scope separation and the SUPER_ADMIN mandatory
# password change. Exits 0 only when every check passes.
#
# Usage (from the repository root, with the "full" compose profile up):
#   scripts/smoke-test.sh
#
# Configuration (environment variables; .env in the repository root is loaded if present):
#   API_URL                          api base URL            (default http://localhost:${API_PORT:-8080}/api/v1)
#   WEB_URL                          web origin, "" to skip  (default http://localhost:${WEB_PORT:-4200})
#   KUADOR_BOOTSTRAP_ADMIN_EMAIL     initial SUPER_ADMIN email (required)
#   KUADOR_BOOTSTRAP_ADMIN_PASSWORD  initial SUPER_ADMIN password (required while it has not been changed)
#   SMOKE_SUPER_ADMIN_NEW_PASSWORD   password the test sets on the SUPER_ADMIN on first run and uses
#                                    afterwards (required, local only, >= 12 chars, never commit it)
#
# Requirements: bash, curl, jq, openssl. Secrets and tokens are never printed.
# shellcheck disable=SC2016  # single-quoted strings are jq programs, not shell expansions

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${ROOT_DIR}/.env" ]]; then
  # Values already exported in the shell win over .env.
  while IFS='=' read -r name value; do
    [[ -z "${name}" || "${name}" == \#* ]] && continue
    [[ -n "${!name+x}" ]] && continue
    export "${name}=${value}"
  done < "${ROOT_DIR}/.env"
fi

API_URL="${API_URL:-http://localhost:${API_PORT:-8080}/api/v1}"
WEB_URL="${WEB_URL-http://localhost:${WEB_PORT:-4200}}"
ADMIN_EMAIL="${KUADOR_BOOTSTRAP_ADMIN_EMAIL:-}"
ADMIN_INITIAL_PASSWORD="${KUADOR_BOOTSTRAP_ADMIN_PASSWORD:-}"
ADMIN_NEW_PASSWORD="${SMOKE_SUPER_ADMIN_NEW_PASSWORD:-}"

for tool in curl jq openssl; do
  command -v "${tool}" >/dev/null || { echo "Missing required tool: ${tool}" >&2; exit 2; }
done
if [[ -z "${ADMIN_EMAIL}" || -z "${ADMIN_NEW_PASSWORD}" ]]; then
  echo "Set KUADOR_BOOTSTRAP_ADMIN_EMAIL and SMOKE_SUPER_ADMIN_NEW_PASSWORD (in .env or the shell)." >&2
  exit 2
fi
if (( ${#ADMIN_NEW_PASSWORD} < 12 )); then
  echo "SMOKE_SUPER_ADMIN_NEW_PASSWORD must have at least 12 characters." >&2
  exit 2
fi

PASSED=0
FAILED=0
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
BODY_FILE="${WORK_DIR}/body.json"

# request METHOD PATH [TOKEN] [JSON_BODY] -> sets STATUS and BODY (base URL: $BASE, default $API_URL)
request() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-sS -o "${BODY_FILE}" -w '%{http_code}' -X "${method}" -H 'Accept: application/json, application/problem+json')
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${data}" ]] && args+=(-H 'Content-Type: application/json' --data-binary "${data}")
  STATUS="$(curl "${args[@]}" "${BASE:-${API_URL}}${path}" || echo 000)"
  BODY="$(cat "${BODY_FILE}" 2>/dev/null || true)"
}

pass() { PASSED=$((PASSED + 1)); printf '  PASS  %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf '  FAIL  %s (HTTP %s, code %s)\n' "$1" "${STATUS}" "$(jq -r '.code? // "-"' <<<"${BODY}" 2>/dev/null || echo '-')"; }

# expect DESCRIPTION HTTP_STATUS [ERROR_CODE] [JQ_FILTER that must be true]
expect() {
  local desc="$1" want_status="$2" want_code="${3:-}" filter="${4:-}"
  if [[ "${STATUS}" != "${want_status}" ]]; then fail "${desc}"; return 0; fi
  if [[ -n "${want_code}" && "$(jq -r '.code? // empty' <<<"${BODY}" 2>/dev/null)" != "${want_code}" ]]; then fail "${desc}"; return 0; fi
  if [[ -n "${filter}" && "$(jq -r "${filter}" <<<"${BODY}" 2>/dev/null)" != "true" ]]; then fail "${desc}"; return 0; fi
  pass "${desc}"
}

json() { jq -cn "$@"; }

login() { # login EMAIL PASSWORD -> STATUS/BODY; TOKEN on success
  request POST /auth/login "" "$(json --arg e "$1" --arg p "$2" '{email:$e,password:$p}')"
  TOKEN="$(jq -r '.accessToken? // empty' <<<"${BODY}" 2>/dev/null || true)"
}

RUN_ID="$(date +%s)-$(openssl rand -hex 3)"
PASSWORD_A="$(openssl rand -hex 16)"
PASSWORD_B="$(openssl rand -hex 16)"
EMAIL_A="smoke-${RUN_ID}-a@example.test"
EMAIL_B="smoke-${RUN_ID}-b@example.test"

echo "Smoke test against ${API_URL} (run ${RUN_ID})"

echo "System"
request GET /system/info
expect "GET /system/info is UP with database UP" 200 "" '.status == "UP" and .database == "UP"'
request GET /me
expect "GET /me without token is rejected" 401 UNAUTHENTICATED

echo "Self-signup and login"
request POST /tenants/signup "" "$(json --arg e "${EMAIL_A}" --arg p "${PASSWORD_A}" '{clinicName:"Smoke Clinic A",ownerDisplayName:"Smoke Owner A",email:$e,password:$p}')"
expect "Signup of clinic A" 201 "" '.tenantId | type == "string"'
TENANT_A="$(jq -r '.tenantId? // empty' <<<"${BODY}")"
request POST /tenants/signup "" "$(json --arg e "${EMAIL_B}" --arg p "${PASSWORD_B}" '{clinicName:"Smoke Clinic B",ownerDisplayName:"Smoke Owner B",email:$e,password:$p}')"
expect "Signup of clinic B" 201 "" '.tenantId | type == "string"'
request POST /tenants/signup "" "$(json --arg e "${EMAIL_A}" --arg p "${PASSWORD_A}" '{clinicName:"Duplicate",ownerDisplayName:"Duplicate",email:$e,password:$p}')"
expect "Signup with an already registered email" 409 EMAIL_TAKEN

login "${EMAIL_A}" "wrong-password-value"
expect "Login with a wrong password" 401 INVALID_CREDENTIALS
login "${EMAIL_A}" "${PASSWORD_A}"
expect "Login of owner A (TENANT scope)" 200 "" '.scope == "TENANT" and .mustChangePassword == false'
TOKEN_A="${TOKEN}"
login "${EMAIL_B}" "${PASSWORD_B}"
expect "Login of owner B (TENANT scope)" 200 "" '.scope == "TENANT"'
TOKEN_B="${TOKEN}"

request GET /me "${TOKEN_A}"
expect "Owner A has OWNER and ADMIN in its own clinic" 200 "" \
  "(.tenant.id == \"${TENANT_A}\") and (.roles | index(\"OWNER\") != null and index(\"ADMIN\") != null)"

echo "Role CRUD (clinic A)"
request GET /permissions "${TOKEN_A}"
expect "Assignable catalog lists only TENANT permissions" 200 "" 'length > 0 and all(.[]; .scope == "TENANT")'
request GET /roles "${TOKEN_A}"
expect "Clinic A lists its system roles OWNER and ADMIN" 200 "" \
  '[.[] | select(.systemRole) | .name] | sort == ["ADMIN","OWNER"]'
OWNER_ROLE_ID="$(jq -r '.[] | select(.name == "OWNER") | .id' <<<"${BODY}")"

request POST /roles "${TOKEN_A}" '{"name":"Smoke Reception","description":"Front desk","permissions":["roles.read"]}'
expect "Create role" 201 "" '.name == "Smoke Reception" and .systemRole == false and .permissions == ["roles.read"]'
ROLE_ID="$(jq -r '.id? // empty' <<<"${BODY}")"
ROLE_VERSION="$(jq -r '.version? // 0' <<<"${BODY}")"
request POST /roles "${TOKEN_A}" '{"name":"smoke reception","permissions":[]}'
expect "Create role with a duplicate name (case-insensitive)" 409 ROLE_NAME_TAKEN
request POST /roles "${TOKEN_A}" '{"name":"Smoke Invalid","permissions":["does.not.exist"]}'
expect "Create role with a permission outside the catalog" 400 VALIDATION
request POST /roles "${TOKEN_A}" '{"name":"Smoke Platform","permissions":["platform.permissions.read"]}'
expect "Create role with a PLATFORM permission" 400 VALIDATION

request GET "/roles/${ROLE_ID}" "${TOKEN_A}"
expect "Get role" 200 "" ".id == \"${ROLE_ID}\""
request PUT "/roles/${ROLE_ID}" "${TOKEN_A}" "$(json --argjson v "${ROLE_VERSION}" '{name:"Smoke Front Desk",description:"Front desk and phone",permissions:["roles.read","roles.create"],version:$v}')"
expect "Update role" 200 "" '.name == "Smoke Front Desk" and (.permissions | sort) == ["roles.create","roles.read"]'
request PUT "/roles/${ROLE_ID}" "${TOKEN_A}" "$(json --argjson v "${ROLE_VERSION}" '{name:"Smoke Stale",permissions:[],version:$v}')"
expect "Update role with a stale version" 409 VERSION_CONFLICT
request GET "/roles/${ROLE_ID}" "${TOKEN_A}"
expect "Update is reflected on read" 200 "" '.name == "Smoke Front Desk"'
ROLE_VERSION="$(jq -r '.version? // 0' <<<"${BODY}")"

request DELETE "/roles/${OWNER_ROLE_ID}" "${TOKEN_A}"
expect "Delete protected role OWNER is rejected" 409 SYSTEM_ROLE_IMMUTABLE
request PUT "/roles/${OWNER_ROLE_ID}" "${TOKEN_A}" '{"name":"OWNER","permissions":[],"version":0}'
expect "Update protected role OWNER is rejected" 409 SYSTEM_ROLE_IMMUTABLE

echo "Cross-tenant isolation (clinic B on clinic A's role)"
request GET "/roles/${ROLE_ID}" "${TOKEN_B}"
expect "B cannot read A's role" 404 NOT_FOUND
request PUT "/roles/${ROLE_ID}" "${TOKEN_B}" "$(json --argjson v "${ROLE_VERSION}" '{name:"Hijacked",permissions:[],version:$v}')"
expect "B cannot update A's role" 404 NOT_FOUND
request DELETE "/roles/${ROLE_ID}" "${TOKEN_B}"
expect "B cannot delete A's role" 404 NOT_FOUND
request GET /roles "${TOKEN_B}"
expect "B's role list does not include A's roles" 200 "" \
  "(map(.id) | index(\"${ROLE_ID}\") == null) and (map(.id) | index(\"${OWNER_ROLE_ID}\") == null)"
request GET "/roles/${ROLE_ID}" "${TOKEN_A}"
expect "A's role is untouched after B's attempts" 200 "" '.name == "Smoke Front Desk"'

echo "Role delete (clinic A)"
request DELETE "/roles/${ROLE_ID}" "${TOKEN_A}"
expect "Delete role" 204
request GET "/roles/${ROLE_ID}" "${TOKEN_A}"
expect "Deleted role is gone" 404 NOT_FOUND

echo "Scope separation"
request GET /platform/permissions "${TOKEN_A}"
expect "TENANT token on /platform/** is rejected" 403 FORBIDDEN

echo "SUPER_ADMIN"
ADMIN_TOKEN=""
if [[ -n "${ADMIN_INITIAL_PASSWORD}" ]]; then
  login "${ADMIN_EMAIL}" "${ADMIN_INITIAL_PASSWORD}"
else
  STATUS=401 BODY='{}'
fi
if [[ "${STATUS}" == "200" ]]; then
  # First run on this database: the initial password is still active.
  expect "Initial login is PLATFORM scope and requires a password change" 200 "" \
    '.scope == "PLATFORM" and .mustChangePassword == true'
  ADMIN_TOKEN="${TOKEN}"
  request GET /me "${ADMIN_TOKEN}"
  expect "GET /me is allowed while the password change is pending" 200 "" '.roles == ["SUPER_ADMIN"] and .mustChangePassword == true'
  request GET /platform/permissions "${ADMIN_TOKEN}"
  expect "Platform endpoint is blocked until the password changes" 403 PASSWORD_CHANGE_REQUIRED
  request GET /roles "${ADMIN_TOKEN}"
  expect "Clinic endpoint is blocked until the password changes" 403 PASSWORD_CHANGE_REQUIRED
  request POST /auth/password "${ADMIN_TOKEN}" "$(json --arg c "${ADMIN_INITIAL_PASSWORD}" '{currentPassword:$c,newPassword:"short"}')"
  expect "Weak new password is rejected" 400 WEAK_PASSWORD
  request POST /auth/password "${ADMIN_TOKEN}" "$(json --arg c "${ADMIN_INITIAL_PASSWORD}" --arg n "${ADMIN_NEW_PASSWORD}" '{currentPassword:$c,newPassword:$n}')"
  expect "Change the initial password" 204
  if [[ -n "${ADMIN_INITIAL_PASSWORD}" ]]; then
    login "${ADMIN_EMAIL}" "${ADMIN_INITIAL_PASSWORD}"
    expect "Initial password is rejected after the change" 401 INVALID_CREDENTIALS
  fi
else
  # Later runs: the initial password must already be rejected.
  if [[ -n "${ADMIN_INITIAL_PASSWORD}" ]]; then
    expect "Initial password is rejected (already changed on a previous run)" 401 INVALID_CREDENTIALS
  fi
fi

login "${ADMIN_EMAIL}" "${ADMIN_NEW_PASSWORD}"
expect "Login with the new password, no change pending" 200 "" '.scope == "PLATFORM" and .mustChangePassword == false'
ADMIN_TOKEN="${TOKEN}"
request GET /platform/permissions "${ADMIN_TOKEN}"
expect "SUPER_ADMIN lists the full catalog" 200 "" 'any(.[]; .scope == "PLATFORM") and any(.[]; .scope == "TENANT")'
request GET /roles "${ADMIN_TOKEN}"
expect "SUPER_ADMIN cannot read clinic data" 403 FORBIDDEN
request GET /permissions "${ADMIN_TOKEN}"
expect "SUPER_ADMIN cannot use clinic endpoints" 403 FORBIDDEN

if [[ -n "${WEB_URL}" ]]; then
  echo "Web container (${WEB_URL})"
  BASE="${WEB_URL}" request GET /
  if [[ "${STATUS}" == "200" ]] && grep -qi '<app-root\|<html' "${BODY_FILE}"; then pass "Web serves the Angular app"; else fail "Web serves the Angular app"; fi
  BASE="${WEB_URL}" request GET /config.json
  expect "Web runtime config has API_BASE_URL" 200 "" '.API_BASE_URL | type == "string" and length > 0'
  WEB_API_BASE="$(jq -r '.API_BASE_URL' <<<"${BODY}" 2>/dev/null || echo /api/v1)"
  [[ "${WEB_API_BASE}" == /* ]] && WEB_API_BASE="${WEB_URL}${WEB_API_BASE}"
  BASE="${WEB_API_BASE}" request GET /system/info
  expect "API reachable from the web's API_BASE_URL" 200 "" '.database == "UP"'
  BASE="${WEB_API_BASE}" request GET /roles "${TOKEN_A}"
  expect "Authenticated call through the web reaches the database" 200 "" 'length >= 2'
  BASE="${WEB_URL}" request GET /some/deep/link
  if [[ "${STATUS}" == "200" ]] && grep -qi '<html' "${BODY_FILE}"; then pass "Web falls back to index.html on deep links"; else fail "Web falls back to index.html on deep links"; fi
fi

echo
echo "Result: ${PASSED} passed, ${FAILED} failed"
(( FAILED == 0 ))
