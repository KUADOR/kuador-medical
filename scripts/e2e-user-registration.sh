#!/usr/bin/env bash
# End-to-end scenarios of the user registration epic (MEDK-12, task MEDK-25) against a running api.
#
# Runs the acceptance scenarios of the spec through the real HTTP API, the same calls that the web
# and the mobile app make:
#   1. Invitation and deep link        5. Minor patient with a guardian
#   2. Login with two clinics          6. Self-registration enabled and disabled
#   3. Branch switch with permissions  7. Tenant A trying to reach tenant B data
#   4. Password reset
#
# Every request of the user registration contract goes through a Prism validating proxy
# (CONTRACT_URL), so any difference between the api and contracts/user-registration.openapi.yaml
# is reported as a failure («contract» lines). Emails are read from Mailpit.
#
# Usage (from the repository root, with db, mailpit and the api up; see docs/deployment/changes/MEDK-25.md):
#   (cd web && npx prism proxy ../contracts/user-registration.openapi.yaml "$API_URL" --port 4011 --errors) &
#   scripts/e2e-user-registration.sh
#
# Configuration (environment variables; .env in the repository root is loaded if present):
#   API_URL        api base URL                                 (default http://localhost:${API_PORT:-8080}/api/v1)
#   CONTRACT_URL   Prism proxy over the api, "" to call the api directly (default http://localhost:4011)
#   MAILPIT_URL    Mailpit base URL                             (default http://localhost:${MAILPIT_UI_PORT:-8025})
#   WEB_URL        web origin for the deep link checks, "" to skip (default http://localhost:${WEB_PORT:-4200})
#   LINK_BASE_URL  expected base of the email links             (default KUADOR_APP_LINK_BASE_URL or KUADOR_WEB_BASE_URL)
#   E2E_PSQL       command that runs psql on the local database, reading SQL from stdin
#                  (default: docker compose exec -T db psql -U $DB_USERNAME -d $DB_NAME)
#
# Branches have no API yet (clinics epic), so the script inserts two branches of the test clinic
# with E2E_PSQL. Local or disposable databases only. Requirements: bash, curl, jq, openssl.
# Passwords and tokens are generated per run and never printed.
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
CONTRACT_URL="${CONTRACT_URL-http://localhost:4011}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:${MAILPIT_UI_PORT:-8025}}"
WEB_URL="${WEB_URL-http://localhost:${WEB_PORT:-4200}}"
LINK_BASE_URL="${LINK_BASE_URL:-${KUADOR_APP_LINK_BASE_URL:-${KUADOR_WEB_BASE_URL:-http://localhost:4200}}}"
LINK_BASE_URL="${LINK_BASE_URL%/}"

for tool in curl jq openssl; do
  command -v "${tool}" >/dev/null || { echo "Missing required tool: ${tool}" >&2; exit 2; }
done

PASSED=0
FAILED=0
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT
BODY_FILE="${WORK_DIR}/body.json"
HEADERS_FILE="${WORK_DIR}/headers.txt"

pass() { PASSED=$((PASSED + 1)); printf '  PASS  %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf '  FAIL  %s (HTTP %s, code %s)\n' "$1" "${STATUS}" "$(jq -r '.code? // "-"' <<<"${BODY}" 2>/dev/null || echo '-')"; }

# request METHOD PATH [TOKEN] [JSON_BODY] -> STATUS, BODY. Contract operations go through CONTRACT_URL.
request() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}" base="${CONTRACT_URL:-${API_URL}}"
  [[ "${path}" == /tenants/* ]] && base="${API_URL}" # identity.openapi.yaml, outside this contract
  local args=(-sS -o "${BODY_FILE}" -D "${HEADERS_FILE}" -w '%{http_code}' -X "${method}"
    -H 'Accept: application/json, application/problem+json')
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${data}" ]] && args+=(-H 'Content-Type: application/json' --data-binary "${data}")
  STATUS="$(curl "${args[@]}" "${base}${path}" || echo 000)"
  BODY="$(cat "${BODY_FILE}" 2>/dev/null || true)"
  # Prism reports differences with the contract in the «sl-violations» header.
  local violations
  violations="$(grep -i '^sl-violations:' "${HEADERS_FILE}" 2>/dev/null | cut -d' ' -f2- | tr -d '\r' || true)"
  if [[ -n "${violations}" ]]; then
    FAILED=$((FAILED + 1))
    printf '  FAIL  contract: %s %s -> %s\n' "${method}" "${path%%\?*}" \
      "$(jq -r '[.[] | "\(.location | join(".")): \(.message)"] | join("; ")' <<<"${violations}" 2>/dev/null || echo "${violations}")"
  fi
}

# raw METHOD PATH [TOKEN] [JSON_BODY]: straight to the api, for requests that break the contract on
# purpose (missing fields) and that the validating proxy would reject before reaching the api.
raw() { local saved="${CONTRACT_URL}"; CONTRACT_URL=""; request "$@"; CONTRACT_URL="${saved}"; }

# expect DESCRIPTION HTTP_STATUS [ERROR_CODE] [JQ_FILTER that must be true]
expect() {
  local desc="$1" want_status="$2" want_code="${3:-}" filter="${4:-}"
  if [[ "${STATUS}" != "${want_status}" ]]; then fail "${desc}"; return 0; fi
  if [[ -n "${want_code}" && "$(jq -r '.code? // empty' <<<"${BODY}" 2>/dev/null)" != "${want_code}" ]]; then fail "${desc}"; return 0; fi
  if [[ -n "${filter}" && "$(jq -r "${filter}" <<<"${BODY}" 2>/dev/null)" != "true" ]]; then fail "${desc}"; return 0; fi
  pass "${desc}"
}

check() { # check DESCRIPTION CONDITION_EXIT_CODE
  if [[ "$2" == 0 ]]; then pass "$1"; else STATUS="-"; BODY=""; fail "$1"; fi
}

json() { jq -cn "$@"; }
uuid() { if command -v uuidgen >/dev/null; then uuidgen | tr '[:upper:]' '[:lower:]'; else cat /proc/sys/kernel/random/uuid; fi; }
field() { jq -r "$1 // empty" <<<"${BODY}" 2>/dev/null || true; }

login() { # login EMAIL PASSWORD -> STATUS/BODY; TOKEN, PRE_AUTH
  request POST /auth/login "" "$(json --arg e "$1" --arg p "$2" '{email:$e,password:$p}')"
  TOKEN="$(field .accessToken)"
  PRE_AUTH="$(field .preAuthToken)"
}

# mail_token EMAIL PATH [SUBJECT_TEXT] -> MAIL_TOKEN and LAST_LINK of the newest email to EMAIL
# (and whose subject contains SUBJECT_TEXT) with a link to PATH. Empty when none arrives.
mail_token() {
  local email="$1" path="$2" subject="${3:-}" query ids message text
  query="to:\"${email}\""
  [[ -n "${subject}" ]] && query="${query} subject:\"${subject}\""
  MAIL_TOKEN=""
  LAST_LINK=""
  for _ in $(seq 1 20); do
    ids="$(curl -sS -G "${MAILPIT_URL}/api/v1/search" --data-urlencode "query=${query}" \
      | jq -r '.messages | sort_by(.Created) | reverse | .[].ID' 2>/dev/null || true)"
    for message in ${ids}; do
      text="$(curl -sS "${MAILPIT_URL}/api/v1/message/${message}" | jq -r '.Text // empty')"
      LAST_LINK="$(grep -oE "https?://[^ )>\"]+${path}#t=[A-Za-z0-9_-]+" <<<"${text}" | head -1 || true)"
      if [[ -n "${LAST_LINK}" ]]; then
        MAIL_TOKEN="${LAST_LINK##*#t=}"
        return 0
      fi
    done
    sleep 0.5
  done
}

psql_local() {
  if [[ -n "${E2E_PSQL:-}" ]]; then
    ${E2E_PSQL}
  else
    (cd "${ROOT_DIR}" && docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "${DB_USERNAME:-medical}" -d "${DB_NAME:-medical}")
  fi
}

RUN_ID="$(date +%s)-$(openssl rand -hex 3)"
pw() { printf 'E2e-%s' "$(openssl rand -hex 12)"; }
OWNER_A_EMAIL="e2e-${RUN_ID}-owner-a@example.test"
OWNER_B_EMAIL="e2e-${RUN_ID}-owner-b@example.test"
STAFF_EMAIL="e2e-${RUN_ID}-staff@example.test"
SELF_EMAIL="e2e-${RUN_ID}-patient@example.test"
OWNER_A_PASSWORD="$(pw)"
OWNER_B_PASSWORD="$(pw)"
STAFF_PASSWORD="$(pw)"
STAFF_NEW_PASSWORD="$(pw)"
SELF_PASSWORD="$(pw)"

echo "E2E user registration against ${API_URL} (contract proxy: ${CONTRACT_URL:-none}, run ${RUN_ID})"

echo "Setup: two clinics, two branches in clinic A and one in clinic B"
request POST /tenants/signup "" "$(json --arg e "${OWNER_A_EMAIL}" --arg p "${OWNER_A_PASSWORD}" '{clinicName:"E2E Clínica A",ownerDisplayName:"Dueña A",email:$e,password:$p}')"
expect "Signup of clinic A" 201 "" '.tenantId | type == "string"'
TENANT_A="$(field .tenantId)"
request POST /tenants/signup "" "$(json --arg e "${OWNER_B_EMAIL}" --arg p "${OWNER_B_PASSWORD}" '{clinicName:"E2E Clínica B",ownerDisplayName:"Dueño B",email:$e,password:$p}')"
expect "Signup of clinic B" 201 "" '.tenantId | type == "string"'
TENANT_B="$(field .tenantId)"
BRANCH_MAIN="$(uuid)"
BRANCH_B="$(uuid)"
BRANCH_NORTH="$(uuid)"
if [[ ! "${TENANT_A}" =~ ^[0-9a-f-]{36}$ ]] || ! psql_local >/dev/null <<SQL
insert into clinics_branch (id, tenant_id, name) values
  ('${BRANCH_MAIN}', '${TENANT_A}', 'Matriz'),
  ('${BRANCH_NORTH}', '${TENANT_A}', 'Sucursal Norte'),
  ('${BRANCH_B}', '${TENANT_B}', 'Matriz B');
SQL
then
  echo "Could not insert the branches of clinic A (set E2E_PSQL or start the db service)." >&2
  exit 2
fi
pass "Branches inserted: Matriz and Sucursal Norte (clinic A), Matriz B (clinic B)"

login "${OWNER_A_EMAIL}" "${OWNER_A_PASSWORD}"
if [[ "$(field .status)" == "BRANCH_SELECTION_REQUIRED" ]]; then
  request POST /auth/login/branch "" "$(json --arg t "${PRE_AUTH}" --arg b "${BRANCH_MAIN}" '{preAuthToken:$t,branchId:$b}')"
  TOKEN="$(field .accessToken)"
fi
expect "Owner A is authenticated" 200 "" '.status == "AUTHENTICATED"'
TOKEN_OWNER_A="${TOKEN}"
login "${OWNER_B_EMAIL}" "${OWNER_B_PASSWORD}"
expect "Owner B is authenticated" 200 "" '.status == "AUTHENTICATED"'
TOKEN_OWNER_B="${TOKEN}"

role_id() { # role_id TOKEN NAME
  request GET /roles "$1"
  jq -r --arg n "$2" '(if type == "array" then . else .items end)[] | select(.name == $n) | .id' <<<"${BODY}" | head -1
}
ROLE_ADMIN_A="$(role_id "${TOKEN_OWNER_A}" ADMIN)"
ROLE_RECEPTION_A="$(role_id "${TOKEN_OWNER_A}" "Recepción")"
ROLE_DOCTOR_B="$(role_id "${TOKEN_OWNER_B}" "Médico")"
check "Seeded roles ADMIN and Recepción (A) and Médico (B) exist" \
  "$([[ -n "${ROLE_ADMIN_A}" && -n "${ROLE_RECEPTION_A}" && -n "${ROLE_DOCTOR_B}" ]] && echo 0 || echo 1)"

echo "1. Invitation and deep link"
request POST /staff-users "${TOKEN_OWNER_A}" "$(json --arg e "${STAFF_EMAIL}" --arg m "${BRANCH_MAIN}" --arg n "${BRANCH_NORTH}" --arg admin "${ROLE_ADMIN_A}" --arg rec "${ROLE_RECEPTION_A}" \
  '{firstNames:"Carla",lastNames:"Prueba",email:$e,jobTitle:"Coordinadora",roleAssignments:{mode:"PER_BRANCH",assignments:[{branchId:$m,roleId:$admin},{branchId:$n,roleId:$rec}]}}')"
expect "Owner A invites a staff user (ADMIN in Matriz, Recepción in Norte)" 201 "" '.status == "INVITED" and (.roleAssignments | length) == 2'
STAFF_A_ID="$(field .id)"
mail_token "${STAFF_EMAIL}" /invitation "E2E Clínica A"
FIRST_INVITATION="${MAIL_TOKEN}"
if [[ -z "${FIRST_INVITATION}" ]]; then
  echo "No invitation email reached Mailpit (${MAILPIT_URL}): check KUADOR_SMTP_* / KUADOR_MAIL_FROM and the api log." >&2
  exit 1
fi
check "Invitation email carries a deep link ${LINK_BASE_URL}/invitation#t=<token>" \
  "$([[ -n "${FIRST_INVITATION}" && "${LAST_LINK}" == "${LINK_BASE_URL}/invitation#t="* ]] && echo 0 || echo 1)"
request POST "/staff-users/${STAFF_A_ID}/invitation" "${TOKEN_OWNER_A}"
expect "Owner A resends the invitation" 202
for _ in $(seq 1 20); do
  mail_token "${STAFF_EMAIL}" /invitation "E2E Clínica A"
  [[ "${MAIL_TOKEN}" != "${FIRST_INVITATION}" ]] && break
  sleep 0.5
done
INVITATION_A="${MAIL_TOKEN}"
request POST /auth/invitations/preview "" "$(json --arg t "${FIRST_INVITATION}" '{token:$t}')"
expect "The replaced invitation link is rejected" 400 ONE_TIME_TOKEN_INVALID
request POST /auth/invitations/preview "" "$(json --arg t "${INVITATION_A}" '{token:$t}')"
expect "Invitation preview shows the clinic and a masked email" 200 "" '.clinicName == "E2E Clínica A" and .kind == "STAFF" and (.maskedEmail | contains("*"))'
request POST /auth/invitations/accept "" "$(json --arg t "${INVITATION_A}" --arg p "${STAFF_PASSWORD}" '{token:$t,password:$p}')"
expect "The staff user accepts the invitation and defines a password" 200
request POST /auth/invitations/accept "" "$(json --arg t "${INVITATION_A}" --arg p "${STAFF_PASSWORD}" '{token:$t,password:$p}')"
expect "An already used invitation is rejected" 400 ONE_TIME_TOKEN_INVALID
request POST /auth/invitations/preview "" '{"token":"not-a-real-invitation-token"}'
expect "An unknown invitation token is rejected" 400 ONE_TIME_TOKEN_INVALID
if [[ -n "${WEB_URL}" ]]; then
  STATUS="$(curl -sS -o "${BODY_FILE}" -w '%{http_code}' "${WEB_URL}/.well-known/assetlinks.json" || echo 000)"; BODY="$(cat "${BODY_FILE}")"
  expect "The web serves /.well-known/assetlinks.json (Android App Links)" 200 "" 'type == "array"'
  STATUS="$(curl -sS -o "${BODY_FILE}" -w '%{http_code}' "${WEB_URL}/.well-known/apple-app-site-association" || echo 000)"; BODY="$(cat "${BODY_FILE}")"
  expect "The web serves /.well-known/apple-app-site-association (iOS Universal Links)" 200 "" '.applinks | type == "object"'
  STATUS="$(curl -sS -o "${BODY_FILE}" -w '%{http_code}' "${WEB_URL}/invitation" || echo 000)"; BODY=""
  check "Without the app the link falls back to the web page /invitation" "$([[ "${STATUS}" == 200 ]] && grep -qi '<mk-root' "${BODY_FILE}" && echo 0 || echo 1)"
fi

echo "2. Login with two clinics"
raw POST /staff-users "${TOKEN_OWNER_B}" "$(json --arg e "${STAFF_EMAIL}" --arg r "${ROLE_DOCTOR_B}" \
  '{firstNames:"Carla",lastNames:"Prueba",email:$e,jobTitle:"Médica general",roleAssignments:{mode:"ALL_BRANCHES",roleId:$r}}')"
expect "A clinical role without professional registration is rejected" 400
request POST /staff-users "${TOKEN_OWNER_B}" "$(json --arg e "${STAFF_EMAIL}" --arg r "${ROLE_DOCTOR_B}" \
  '{firstNames:"Carla",lastNames:"Prueba",email:$e,jobTitle:"Médica general",professionalRegistration:"MSP-E2E-001",roleAssignments:{mode:"ALL_BRANCHES",roleId:$r}}')"
expect "Owner B invites the same email as an independent user" 201
STAFF_B_ID="$(field .id)"
mail_token "${STAFF_EMAIL}" /invitation "E2E Clínica B"
INVITATION_B="${MAIL_TOKEN}"
request POST /auth/invitations/accept "" "$(json --arg t "${INVITATION_B}" --arg p "${STAFF_PASSWORD}" '{token:$t,password:$p}')"
expect "The invitation of clinic B is accepted with the same password" 200
login "${STAFF_EMAIL}" "wrong-password-value"
expect "Wrong password: generic error" 401 INVALID_CREDENTIALS
login "nobody-${RUN_ID}@example.test" "wrong-password-value"
expect "Unknown email: the same generic error" 401 INVALID_CREDENTIALS
login "${STAFF_EMAIL}" "${STAFF_PASSWORD}"
expect "Credentials matching in two clinics ask to choose one" 200 "" \
  '.status == "CLINIC_SELECTION_REQUIRED" and ([.clinics[].name] | sort) == ["E2E Clínica A","E2E Clínica B"]'
PRE_AUTH_CLINICS="${PRE_AUTH}"
request POST /auth/login/clinic "" "$(json --arg t "${PRE_AUTH_CLINICS}" --arg c "${TENANT_B}" '{preAuthToken:$t,clinicId:$c}')"
expect "Choosing clinic B opens a session bound to clinic B" 200 "" '.status == "AUTHENTICATED" and .clinic.name == "E2E Clínica B"'
TOKEN_STAFF_B="$(field .accessToken)"
request GET /me "${TOKEN_STAFF_B}"
expect "/me of the clinic B session shows clinic B and the Médico role" 200 "" '.clinic.name == "E2E Clínica B" and (.roles | index("Médico")) != null'
login "${STAFF_EMAIL}" "${STAFF_PASSWORD}"
request POST /auth/login/clinic "" "$(json --arg t "${PRE_AUTH}" --arg c "${TENANT_A}" '{preAuthToken:$t,clinicId:$c}')"
expect "Choosing clinic A (two branches) asks to choose a branch" 200 "" '.status == "BRANCH_SELECTION_REQUIRED" and (.branches | length) == 2'
PRE_AUTH_BRANCH="$(field .preAuthToken)"

echo "3. Branch switch with different permissions"
request POST /auth/login/branch "" "$(json --arg t "${PRE_AUTH_BRANCH}" --arg b "${BRANCH_MAIN}" '{preAuthToken:$t,branchId:$b}')"
expect "Choosing Matriz opens the session in Matriz" 200 "" '.status == "AUTHENTICATED" and .branch.name == "Matriz"'
TOKEN_STAFF_A="$(field .accessToken)"
REFRESH_STAFF_A="$(field .refreshToken)"
request GET /me "${TOKEN_STAFF_A}"
expect "In Matriz (ADMIN) the session has identity.users.manage" 200 "" \
  '.activeBranch.name == "Matriz" and (.permissions | index("identity.users.manage")) != null and (.availableBranches | length) == 2'
request GET /staff-users "${TOKEN_STAFF_A}"
expect "In Matriz the staff list is allowed" 200
request POST /auth/switch-branch "${TOKEN_STAFF_A}" "$(json --arg b "${BRANCH_NORTH}" --arg r "${REFRESH_STAFF_A}" '{branchId:$b,refreshToken:$r}')"
expect "Switching to Sucursal Norte issues new tokens" 200 "" '.status == "AUTHENTICATED" and .branch.name == "Sucursal Norte"'
TOKEN_STAFF_NORTH="$(field .accessToken)"
request GET /me "${TOKEN_STAFF_NORTH}"
expect "In Norte (Recepción) the session has patients.register and not identity.users.read" 200 "" \
  '.activeBranch.name == "Sucursal Norte" and (.permissions | index("patients.register")) != null and (.permissions | index("identity.users.read")) == null'
request GET /staff-users "${TOKEN_STAFF_NORTH}"
expect "In Norte the staff list is forbidden" 403
request POST /auth/switch-branch "${TOKEN_STAFF_NORTH}" "$(json --arg b "$(uuid)" '{branchId:$b}')"
expect "Switching to a branch without assignment is rejected" 403 BRANCH_NOT_ASSIGNED

echo "4. Password reset"
request POST /auth/password-reset/request "" "$(json --arg e "nobody-${RUN_ID}@example.test" '{email:$e}')"
expect "Reset of an unknown email answers 202" 202
request POST /auth/password-reset/request "" "$(json --arg e "${STAFF_EMAIL}" '{email:$e}')"
expect "Reset of the staff email answers 202" 202
mail_token "${STAFF_EMAIL}" /password-reset "E2E Clínica A"
RESET_TOKEN_A="${MAIL_TOKEN}"
check "Clinic A reset email with a ${LINK_BASE_URL}/password-reset#t=<token> deep link" \
  "$([[ -n "${RESET_TOKEN_A}" && "${LAST_LINK}" == "${LINK_BASE_URL}/password-reset#t="* ]] && echo 0 || echo 1)"
mail_token "${STAFF_EMAIL}" /password-reset "E2E Clínica B"
check "Clinic B reset email too (one link per clinic)" "$([[ -n "${MAIL_TOKEN}" && "${MAIL_TOKEN}" != "${RESET_TOKEN_A}" ]] && echo 0 || echo 1)"
request POST /auth/password-reset/confirm "" "$(json --arg t "${RESET_TOKEN_A}" --arg p "${STAFF_NEW_PASSWORD}" '{token:$t,newPassword:$p}')"
check "The clinic A link sets a new password (HTTP ${STATUS})" "$([[ "${STATUS}" =~ ^20[04]$ ]] && echo 0 || echo 1)"
login "${STAFF_EMAIL}" "${STAFF_NEW_PASSWORD}"
expect "Credentials matching only clinic A enter clinic A directly (no clinic choice)" 200 "" \
  '.status == "BRANCH_SELECTION_REQUIRED" and .clinic.name == "E2E Clínica A"'
login "${STAFF_EMAIL}" "${STAFF_PASSWORD}"
expect "The old password still opens clinic B (other account)" 200 "" '.status == "AUTHENTICATED" and .clinic.name == "E2E Clínica B"'
request POST /auth/password-reset/confirm "" "$(json --arg t "${RESET_TOKEN_A}" --arg p "$(pw)" '{token:$t,newPassword:$p}')"
expect "A used reset link is rejected" 400 ONE_TIME_TOKEN_INVALID

echo "5. Minor patient with a guardian"
MINOR_BIRTH="$(date -u -v-10y +%Y-%m-%d 2>/dev/null || date -u -d '10 years ago' +%Y-%m-%d)"
MINOR_DOC="E2E${RANDOM}${RANDOM}"
MINOR_BASE="$(json --arg d "${MINOR_DOC}" --arg b "${MINOR_BIRTH}" \
  '{documentType:"NATIONAL_ID",documentNumber:$d,firstNames:"Lucía",lastNames:"Menor",birthDate:$b,sex:"FEMALE",consent:{textVersion:"2026-01",grantedBy:"GUARDIAN"}}')"
raw POST /patients "${TOKEN_STAFF_NORTH}" "${MINOR_BASE}"
expect "A minor without a guardian is rejected" 400
raw POST /patients "${TOKEN_STAFF_NORTH}" "$(jq -c 'del(.consent)' <<<"${MINOR_BASE}")"
expect "A patient without consent is rejected" 400
request POST /patients "${TOKEN_STAFF_NORTH}" "$(jq -c --arg g "G${MINOR_DOC}" \
  '. + {guardian:{relationship:"Madre",person:{documentType:"NATIONAL_ID",documentNumber:$g,firstNames:"María",lastNames:"Tutora"}}}' <<<"${MINOR_BASE}")"
expect "Reception registers the minor with a guardian; no own account" 201 "" '.minor == true and .accountStatus == "NONE"'
MINOR_ID="$(field .id)"
request GET "/patients/${MINOR_ID}/guardian-links" "${TOKEN_STAFF_NORTH}"
expect "The minor has a current LEGAL_GUARDIAN link" 200 "" \
  '[(if type == "array" then . else .items end)[] | select(.linkType == "LEGAL_GUARDIAN" and .current)] | length == 1'
request POST "/patients/${MINOR_ID}/account-invitation" "${TOKEN_STAFF_NORTH}" '{"channel":"EMAIL"}'
check "An account invitation for the minor is rejected (HTTP ${STATUS})" "$([[ "${STATUS}" =~ ^4 ]] && echo 0 || echo 1)"
request POST /patients "${TOKEN_STAFF_NORTH}" "$(jq -c --arg g "G${MINOR_DOC}" \
  '. + {guardian:{relationship:"Madre",person:{documentType:"NATIONAL_ID",documentNumber:$g,firstNames:"María",lastNames:"Tutora"}}}' <<<"${MINOR_BASE}")"
expect "The same document again is rejected as a duplicate" 409

echo "6. Self-registration enabled and disabled"
request GET /patient-registration-settings "${TOKEN_OWNER_B}"
expect "Self-registration is disabled by default" 200 "" '.selfRegistrationEnabled == false'
PUBLIC_CODE_B="$(field .publicCode)"
SETTINGS_VERSION="$(field .version)"
SELF_BODY="$(json --arg d "S${MINOR_DOC}" --arg e "${SELF_EMAIL}" --arg p "${SELF_PASSWORD}" \
  '{documentType:"NATIONAL_ID",documentNumber:$d,firstNames:"Pedro",lastNames:"Autorregistro",birthDate:"1990-05-04",sex:"MALE",email:$e,password:$p,consent:{textVersion:"2026-01"}}')"
request POST "/public/clinics/${PUBLIC_CODE_B}/patient-registrations" "" "${SELF_BODY}"
expect "Self-registration while disabled is not available" 404
request PUT /patient-registration-settings "${TOKEN_OWNER_B}" "$(json --argjson v "${SETTINGS_VERSION}" '{selfRegistrationEnabled:true,version:$v}')"
expect "Owner B enables self-registration" 200 "" '.selfRegistrationEnabled == true'
SETTINGS_VERSION="$(field .version)"
raw POST "/public/clinics/${PUBLIC_CODE_B}/patient-registrations" "" "$(jq -c 'del(.consent)' <<<"${SELF_BODY}")"
expect "Self-registration without consent is rejected" 400
request POST "/public/clinics/${PUBLIC_CODE_B}/patient-registrations" "" "${SELF_BODY}"
expect "Self-registration while enabled is accepted" 202
mail_token "${SELF_EMAIL}" /email-verification
request POST /auth/email-verification/confirm "" "$(json --arg t "${MAIL_TOKEN}" '{token:$t}')"
check "The email verification link activates the account (HTTP ${STATUS})" "$([[ "${STATUS}" =~ ^20[04]$ ]] && echo 0 || echo 1)"
login "${SELF_EMAIL}" "${SELF_PASSWORD}"
expect "The self-registered patient logs in" 200 "" '.status == "AUTHENTICATED" and .clinic.name == "E2E Clínica B"'
TOKEN_PATIENT="${TOKEN}"
request GET /me "${TOKEN_PATIENT}"
expect "The patient session only has patient modules" 200 "" '.kind == "PATIENT" and (.permissions | index("patients.read")) == null'
request GET /patients "${TOKEN_PATIENT}"
expect "The patient cannot list the clinic patients" 403
request PUT /patient-registration-settings "${TOKEN_OWNER_B}" "$(json --argjson v "${SETTINGS_VERSION}" '{selfRegistrationEnabled:false,version:$v}')"
expect "Owner B disables self-registration" 200 "" '.selfRegistrationEnabled == false'
request POST "/public/clinics/${PUBLIC_CODE_B}/patient-registrations" "" "$(jq -c '.documentNumber = "X" + .documentNumber | .email = "x-" + .email' <<<"${SELF_BODY}")"
expect "Self-registration is no longer available" 404

echo "7. Tenant A trying to reach tenant B data"
request GET "/staff-users/${STAFF_B_ID}" "${TOKEN_OWNER_A}"
expect "A reads a B staff user: 404" 404 NOT_FOUND
request PATCH "/staff-users/${STAFF_B_ID}" "${TOKEN_OWNER_A}" '{"jobTitle":"Cambio","version":0}'
expect "A edits a B staff user: 404" 404 NOT_FOUND
request POST "/staff-users/${STAFF_B_ID}/disable" "${TOKEN_OWNER_A}"
expect "A disables a B staff user: 404" 404 NOT_FOUND
request PUT "/roles/${ROLE_DOCTOR_B}" "${TOKEN_OWNER_A}" '{"name":"Hack","permissions":["patients.read"],"version":0}'
expect "A edits a B role: 404" 404 NOT_FOUND
request GET "/patients/${MINOR_ID}" "${TOKEN_OWNER_B}"
expect "B reads an A patient: 404" 404 NOT_FOUND
request GET "/patients/${MINOR_ID}/guardian-links" "${TOKEN_OWNER_B}"
expect "B reads the guardian links of an A patient: 404" 404 NOT_FOUND
request PUT "/staff-users/${STAFF_A_ID}/role-assignments" "${TOKEN_OWNER_B}" "$(json --arg r "${ROLE_DOCTOR_B}" '{mode:"ALL_BRANCHES",roleId:$r}')"
expect "B assigns roles to an A staff user: 404" 404 NOT_FOUND
request GET "/patients?size=100" "${TOKEN_OWNER_B}"
check "The patient list of B does not include A patients" \
  "$(jq -e --arg id "${MINOR_ID}" '[.items[] | select(.id == $id)] | length == 0' <<<"${BODY}" >/dev/null && echo 0 || echo 1)"
request GET "/staff-users?size=100" "${TOKEN_OWNER_A}"
check "The staff list of A does not include the B account of the same email" \
  "$(jq -e --arg id "${STAFF_B_ID}" '[.items[] | select(.id == $id)] | length == 0' <<<"${BODY}" >/dev/null && echo 0 || echo 1)"

echo
echo "Result: ${PASSED} passed, ${FAILED} failed"
(( FAILED == 0 ))
