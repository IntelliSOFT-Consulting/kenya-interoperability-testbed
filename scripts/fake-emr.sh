#!/usr/bin/env bash
# Simulated EMR used to verify mock-hie, lab-gateway and the DHA HIE ITB suite.
# Usage: scripts/fake-emr.sh <mock|gateway|itb|itb-bad>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${FAKE_EMR_ENV:-$ROOT/scripts/.fake-emr.env}"
# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && . "$ENV_FILE"

MOCK_URL="${MOCK_URL:-http://localhost:8095/uat-middleware/api/v1}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:9010}"
FIXTURES="$ROOT/dha-hie-suite/resources/fixtures.json"
# Static unsigned fixture JWT issued by mock-hie (see mock-hie/mappings/token.json).
TOKEN='eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ0ZW5hbnRfaWQiOiJpdGItbW9jay10ZW5hbnQiLCJmYWNpbGl0eV9pZCI6IkZJRC00Ny0xMTUzMDctOCIsImZhY2lsaXR5X2lkX3R5cGUiOiJmci1jb2RlIiwiaXNzIjoibW9jay1oaWUiLCJzdWIiOiJpdGItdGVzdC1jbGllbnQifQ.'

PASS=0
FAIL=0

check() { # <label> <expected> <actual>
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "ok   $1"
  else
    FAIL=$((FAIL + 1)); echo "FAIL $1: expected '$2', got '$3'"
  fi
}

status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

fx() { jq -r "$1" "$FIXTURES"; }

summary() {
  echo "$PASS passed, $FAIL failed"
  [ "$FAIL" -eq 0 ]
}

mode_mock() {
  local form='Content-Type: application/x-www-form-urlencoded'
  check "token: form body -> 200" 200 \
    "$(status_of -X POST "$MOCK_URL/tenants/token" -H "$form" --data 'client_id=a&client_secret=b')"
  check "token: form body with grant_type and scope -> 200" 200 \
    "$(status_of -X POST "$MOCK_URL/tenants/token" -H "$form" \
        --data 'grant_type=client_credentials&client_id=a&client_secret=b&scope=read')"
  check "token: JSON body -> 200" 200 \
    "$(status_of -X POST "$MOCK_URL/tenants/token" -H 'Content-Type: application/json' \
        --data '{"client_id":"a","client_secret":"b"}')"
  check "token: missing client_secret -> 400" 400 \
    "$(status_of -X POST "$MOCK_URL/tenants/token" -H "$form" --data 'client_id=a')"
  check "token: empty client_secret -> 400" 400 \
    "$(status_of -X POST "$MOCK_URL/tenants/token" -H "$form" --data 'client_id=a&client_secret=')"
  check "token: body carries fixture JWT" "$TOKEN" \
    "$(curl -s -X POST "$MOCK_URL/tenants/token" -H "$form" --data 'client_id=a&client_secret=b' | jq -r .access_token)"

  local n i type number resp
  n=$(fx '[.nationalId] + .alternates | length')
  i=0
  while [ "$i" -lt "$n" ]; do
    type=$(fx "([.nationalId] + .alternates)[$i].type")
    number=$(fx "([.nationalId] + .alternates)[$i].number")
    resp=$(curl -s -G "$MOCK_URL/patients" -H "Authorization: Bearer $TOKEN" \
      --data-urlencode "identification_type=$type" --data-urlencode "identification_number=$number" \
      -w '\n%{http_code}' || true)
    check "patients: $type -> 200" 200 "$(tail -n1 <<<"$resp")"
    check "patients: $type echoes number" "$number" "$(sed '$d' <<<"$resp" | jq -r .identification_number)"
    i=$((i + 1))
  done

  local auth="Authorization: Bearer $TOKEN"
  check "patients: not-found number -> 404" 404 \
    "$(status_of -G "$MOCK_URL/patients" -H "$auth" --data-urlencode 'identification_type=National ID' \
        --data-urlencode "identification_number=$(fx .notFound.number)")"
  check "patients: non-enum type NATIONAL_ID -> 400" 400 \
    "$(status_of -G "$MOCK_URL/patients" -H "$auth" --data-urlencode 'identification_type=NATIONAL_ID' \
        --data-urlencode 'identification_number=90000001')"
  check "patients: missing type -> 400" 400 \
    "$(status_of -G "$MOCK_URL/patients" -H "$auth" --data-urlencode 'identification_number=90000001')"
  check "patients: missing number -> 400" 400 \
    "$(status_of -G "$MOCK_URL/patients" -H "$auth" --data-urlencode 'identification_type=National ID')"
  check "patients: no Authorization -> 401" 401 \
    "$(status_of -G "$MOCK_URL/patients" --data-urlencode 'identification_type=National ID' \
        --data-urlencode 'identification_number=90000001')"
  check "patients: malformed Bearer -> 401" 401 \
    "$(status_of -G "$MOCK_URL/patients" -H 'Authorization: Bearer not-a-jwt' \
        --data-urlencode 'identification_type=National ID' --data-urlencode 'identification_number=90000001')"
  check "patients: Basic auth -> 401" 401 \
    "$(status_of -G "$MOCK_URL/patients" -H 'Authorization: Basic dXNlcjpwYXNz' \
        --data-urlencode 'identification_type=National ID' --data-urlencode 'identification_number=90000001')"
}

mode_gateway() {
  local unknown_body mapped_headers mapped_body raw_headers codes n429
  unknown_body=$(curl -s "$GATEWAY_URL/tenants/token" || true)
  check "gateway: unknown host -> 404" 404 "$(status_of "$GATEWAY_URL/tenants/token")"
  check "gateway: unknown host body" '{"error":"Not Found","message":"Unknown vendor host"}' "$unknown_body"
  check "gateway: automation API not exposed" 404 "$(status_of "$GATEWAY_URL/api/rest/tests/status")"

  mapped_headers=$(curl -s -D - -o /dev/null "http://test-vendor.lab.localhost:9010/tenants/token" || true)
  check "gateway: mapped vendor host reaches lab-service" yes \
    "$(grep -qi '^X-Lab-Upstream: lab-service' <<<"$mapped_headers" && echo yes || echo no)"
  mapped_body=$(curl -s "http://test-vendor.lab.localhost:9010/api/rest/tests/status" || true)
  check "gateway: vendor host cannot reach the automation API" no \
    "$(grep -q 'Needs API key header' <<<"$mapped_body" && echo yes || echo no)"

  raw_headers=$(curl -s -D - -o /dev/null "$GATEWAY_URL/itbsrv/api/http/NO-SUCH-KEY/patients" || true)
  check "gateway: raw receive path reaches lab-service" yes \
    "$(grep -qi '^X-Lab-Upstream: lab-service' <<<"$raw_headers" && echo yes || echo no)"

  # Parallel, so the burst exceeds 10 r/s + 20 regardless of how fast lab-service answers.
  codes=$(seq 1 60 | xargs -P 30 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
    "http://test-vendor.lab.localhost:9010/rate-limit-probe" || true)
  n429=$(grep -c '^429$' <<<"$codes" || true)
  check "gateway: burst beyond rate limit gets 429" yes "$([ "$n429" -gt 0 ] && echo yes || echo no)"
}

STATUS_FILE="$(mktemp)"
trap 'rm -f "$STATUS_FILE"' EXIT

require_itb_env() {
  local v
  for v in ITB_UI_URL LAB_ORG_API_KEY LAB_SYSTEM_API_KEY LAB_CLIENT_ID LAB_CLIENT_SECRET; do
    if [ -z "${!v:-}" ]; then echo "set $v in $ENV_FILE (see scripts/fake-emr.env.example)" >&2; exit 2; fi
  done
  EMR_BASE_URL="${EMR_BASE_URL:-$GATEWAY_URL/itbsrv/api/http/$LAB_SYSTEM_API_KEY}"
}

itb_api() { # <start|status> <json body>
  curl -s -X POST "$ITB_UI_URL/api/rest/tests/$1" -H "ITB-API-KEY: $LAB_ORG_API_KEY" \
    -H 'Content-Type: application/json' --data "$2"
}

start_session() { # <test case id> -> prints session id
  local body
  body=$(jq -n --arg sys "$LAB_SYSTEM_API_KEY" --arg actor "${LAB_ACTOR_API_KEY:-}" --arg tc "$1" \
    '{system: $sys, testCase: [$tc],
      inputMapping: [{input: {name: "skipInteractions", value: "true", type: "boolean"}}]}
     + (if $actor == "" then {} else {actor: $actor} end)')
  itb_api start "$body" | jq -r '.createdSessions[0].session // empty'
}

wait_result() { # <session id> -> prints SUCCESS|FAILURE|TIMEOUT; status JSON + report in $STATUS_FILE
  local i=0 result
  while [ "$i" -lt "${RESULT_WAIT:-60}" ]; do
    itb_api status "$(jq -n --arg s "$1" '{session: [$s], withLogs: true, withReports: true}')" > "$STATUS_FILE"
    result=$(jq -r '.sessions[0].result // "UNDEFINED"' "$STATUS_FILE")
    if [ "$result" = "SUCCESS" ] || [ "$result" = "FAILURE" ]; then
      curl -s "$ITB_UI_URL/api/rest/tests/report/$1" -H "ITB-API-KEY: $LAB_ORG_API_KEY" \
        -H 'Accept: application/xml' >> "$STATUS_FILE"
      echo "$result"; return
    fi
    sleep 2; i=$((i + 1))
  done
  echo TIMEOUT
}

run_case() { # <label> <SUCCESS|FAILURE> <text the report must contain, or ''> <test case id> <action fn>
  local session result
  if [ -n "${ONLY:-}" ] && [ "$4" != "$ONLY" ]; then return; fi
  session=$(start_session "$4" || true)
  if [ -z "$session" ]; then check "$1: session started" yes no; return; fi
  sleep "${SESSION_WARMUP:-3}"
  "$5" || true
  result=$(wait_result "$session" || true)
  # A session left waiting at a receive would block the next one (no parallel execution).
  if [ "$result" = "TIMEOUT" ]; then
    itb_api stop "$(jq -n --arg s "$session" '{session: [$s]}')" >/dev/null
  fi
  check "$1: result" "$2" "$result"
  if [ -n "$3" ]; then
    check "$1: report mentions '$3'" yes "$(grep -qF "$3" "$STATUS_FILE" && echo yes || echo no)"
  fi
}

form_token() { # <client_secret> [extra --data-urlencode args...]
  local secret="$1"; shift
  curl -s -o /dev/null -X POST "$EMR_BASE_URL/tenants/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "client_id=$LAB_CLIENT_ID" --data-urlencode "client_secret=$secret" "$@"
}

lookup() { # <type> <number> [extra curl args...]
  local type="$1" number="$2"; shift 2
  curl -s -o /dev/null -G "$EMR_BASE_URL/patients" \
    --data-urlencode "identification_type=$type" --data-urlencode "identification_number=$number" "$@"
}

act_token_ok()          { form_token "$LAB_CLIENT_SECRET"; }
act_token_grant_type()  { form_token "$LAB_CLIENT_SECRET" --data-urlencode 'grant_type=client_credentials' --data-urlencode 'scope=read'; }
act_token_json() {
  curl -s -o /dev/null -X POST "$EMR_BASE_URL/tenants/token" -H 'Content-Type: application/json' \
    --data "$(jq -n --arg id "$LAB_CLIENT_ID" --arg s "$LAB_CLIENT_SECRET" '{client_id: $id, client_secret: $s}')"
}
act_token_bad_secret()  { form_token "wrong-secret"; }
act_cr01_ok()           { lookup "$(fx .nationalId.type)" "$(fx .nationalId.number)" -H "Authorization: Bearer $TOKEN"; }
act_cr01_raw_query() { # '+' for spaces, number first, an extra parameter
  curl -s -o /dev/null "$EMR_BASE_URL/patients?identification_number=$(fx .nationalId.number)&foo=bar&identification_type=National+ID" \
    -H "Authorization: Bearer $TOKEN"
}
act_cr01_facility_ok()  { lookup "$(fx .nationalId.type)" "$(fx .nationalId.number)" -H "Authorization: Bearer $TOKEN" \
    -H 'X-Facility-Id: FID-47-115307-8' -H 'X-Facility-Id-Type: fr-code'; }
act_cr02_ok() {
  local n i=0
  n=$(fx '.alternates | length')
  while [ "$i" -lt "$n" ]; do
    lookup "$(fx ".alternates[$i].type")" "$(fx ".alternates[$i].number")" -H "Authorization: Bearer $TOKEN"
    sleep 2; i=$((i + 1))
  done
}
act_cr03_lowercase_auth() { lookup "$(fx .notFound.type)" "$(fx .notFound.number)" -H "authorization: Bearer $TOKEN"; }
act_cr01_bad_type()     { lookup "NATIONAL_ID" "$(fx .nationalId.number)" -H "Authorization: Bearer $TOKEN"; }
act_cr01_no_bearer()    { lookup "$(fx .nationalId.type)" "$(fx .nationalId.number)"; }
act_cr01_one_facility() { lookup "$(fx .nationalId.type)" "$(fx .nationalId.number)" -H "Authorization: Bearer $TOKEN" \
    -H 'X-Facility-Id: FID-47-115307-8'; }

mode_itb() {
  require_itb_env
  run_case "AUTH-01 form body"                   SUCCESS "" AUTH-01 act_token_ok
  run_case "AUTH-01 with grant_type and scope"   SUCCESS "" AUTH-01 act_token_grant_type
  run_case "AUTH-01 JSON body (warning only)"    SUCCESS "JSON body accepted" AUTH-01 act_token_json
  run_case "CR-01 National ID"                   SUCCESS "" CR-01 act_cr01_ok
  run_case "CR-01 raw query, + encoding, reordered" SUCCESS "" CR-01 act_cr01_raw_query
  run_case "CR-01 with facility headers"         SUCCESS "" CR-01 act_cr01_facility_ok
  run_case "CR-02 other identifier types"        SUCCESS "" CR-02 act_cr02_ok
  run_case "CR-03 lower-case authorization header" SUCCESS "" CR-03 act_cr03_lowercase_auth
}

mode_itb_bad() {
  require_itb_env
  run_case "AUTH-01 wrong secret"        FAILURE "client_secret does not match the secret issued to this system" AUTH-01 act_token_bad_secret
  run_case "CR-01 NATIONAL_ID spelling"  FAILURE "identification_type must be exactly" CR-01 act_cr01_bad_type
  run_case "CR-01 missing Bearer"        FAILURE "Authorization header must carry a Bearer JWT" CR-01 act_cr01_no_bearer
  run_case "CR-01 one facility header"   FAILURE "X-Facility-Id and X-Facility-Id-Type must be sent together" CR-01 act_cr01_one_facility
}

case "${1:-}" in
  mock) mode_mock ;;
  gateway) mode_gateway ;;
  itb) mode_itb ;;
  itb-bad) mode_itb_bad ;;
  *) echo "usage: $0 <mock|gateway|itb|itb-bad>" >&2; exit 2 ;;
esac
summary
