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

case "${1:-}" in
  mock) mode_mock ;;
  *) echo "usage: $0 <mock|gateway|itb|itb-bad>" >&2; exit 2 ;;
esac
summary
