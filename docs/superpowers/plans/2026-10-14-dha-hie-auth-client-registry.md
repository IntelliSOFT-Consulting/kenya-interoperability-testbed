# DHA HIE Auth & Client Registry Conformance Suite: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let ITB conformance-test EMR/facility systems that call the DHA AfyaConnect HIE token and Client Registry APIs. A WireMock mock (`mock-hie`) answers in place of DHA, and a later local UAT replaces it through configuration only.

**Architecture:**
- The EMR calls `lab-gateway` (nginx).
- `lab-gateway` maps a clean vendor host, or the raw ITB receive path, onto `lab-service` (gitb-srv) HttpMessagingV2 `receive` steps.
- Each receive forwards the call to `$DOMAIN{hieBaseUrl}` (`mock-hie`) and relays the upstream reply.
- After replying, the test case checks the recorded request: credentials, Bearer, facility headers, `identification_type` enum and number.
- `fixtures.json` in the suite is the single source of test identities. A generator produces the mock's Client Registry stubs from it.

**Tech Stack:**
- ITB / GITB TDL. The engine is v1.30.1; the receive `result` block needs ≥ 1.30.0.
- WireMock `wiremock/wiremock:3.9.1`.
- nginx `nginx:1.27-alpine`.
- Docker Compose (installed: v5.1.3; `!reset`/`!override` need ≥ 2.24).
- bash + curl + jq; python3 for the fixture generator; xmllint for XSD validation.

**Spec:** `docs/superpowers/specs/2026-10-14-dha-hie-auth-client-registry-design.md`. Read it alongside this plan.

## Global Constraints

- Mock image `wiremock/wiremock:3.9.1`; gateway image `nginx:1.27-alpine`.
- Mock paths are prefixed `/uat-middleware/api/v1`, mirroring UAT `https://ilm-dev.dha.go.ke/uat-middleware/api/v1`.
- `identification_type` enum, exact spelling: `National ID`, `ClientRegistry ID`, `Birth Notification`, `Birth Certificate`, `Alien ID`, `Refugee ID`, `Mandate Number`.
- Domain parameter `hieBaseUrl` = `http://mock-hie:8080/uat-middleware/api/v1`.
- System properties `hieClientId` (Simple), `hieClientSecret` (Secret) and `labGatewayBaseUrl` (Simple, optional).
- Every test case has `supportsParallelExecution="false"`.
- Network aliases: `gitb-srv`→`lab-service`, `gitb-ui`→`lab-ui`, `gitb-ui-proxy`→`lab-ui-proxy`, `gitb-mysql`→`lab-db`, `gitb-redis`→`lab-cache`, `hapi-fhir-kenyaitb`→`lab-fhir`. Service names and `container_name`s stay unchanged.
- New config refers to services by alias. Existing config (FHIR suites, `nginx/gitb-ui-proxy.conf`) is not edited.
- Host ports: `mock-hie` `8095`, `lab-gateway` `9010` (local); `443` in the public overlay.
- Fixture JWT (unsigned, static, facility-scoped):
  - claims `tenant_id: itb-mock-tenant`, `facility_id: FID-47-115307-8`, `facility_id_type: fr-code`;
  - value: `eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ0ZW5hbnRfaWQiOiJpdGItbW9jay10ZW5hbnQiLCJmYWNpbGl0eV9pZCI6IkZJRC00Ny0xMTUzMDctOCIsImZhY2lsaXR5X2lkX3R5cGUiOiJmci1jb2RlIiwiaXNzIjoibW9jay1oaWUiLCJzdWIiOiJpdGItdGVzdC1jbGllbnQifQ.`
- Bearer format regex: `^Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$`.
- Not-found number: `00000000`. All fixture data is synthetic. Never copy example IDs from the DHA guide.
- Switching to UAT must not require test-case XML edits. All upstream URLs come from `$DOMAIN{hieBaseUrl}`.
- Never log or report `Authorization` values or `client_secret`.
- Commit messages have no attribution trailers.

## Deviations from the spec (found while checking ITB 1.30.1 docs and XSD)

1. **No API answers `interact` prompts.** The ITB REST API only has start/status/stop/report.
   - Every test case declares `skipInteractions` (boolean, default `false`) and puts `skipped="$skipInteractions"` on its confirmation prompts.
   - `fake-emr.sh` starts sessions with `inputMapping` `skipInteractions=true`. This is the mechanism ITB documents.
   - Instruction pop-ups use `blocking="false"`, so they never stall a session.
2. **ITB doesn't say whether `{request}{uri}` contains the query string.**
   - `lab-gateway` adds `X-Lab-Original-Uri: $request_uri` to every proxied call.
   - The `normaliseRequest` scriptlet reads the query from the URI if present, otherwise from that header.
   - Because of this, **all EMR traffic goes through `lab-gateway`**, including local runs on `:9010`.
3. **No TDL variable exposes the receive base URL.**
   - Spec §3.2 says the raw URL is `<CALLBACK_ROOT_URL>/itbsrv/api/http/$SYSTEM{apiKey}`.
   - Instead, a new domain parameter `labServiceBaseUrl` holds the gateway's raw receive base (`http://localhost:9010/itbsrv/api/http` locally).
4. **Checks run after the reply, not inside the `result` block.**
   - The `result` block only forwards upstream and relays the reply.
   - The checks then read `$emrCall{request}` and `$emrCall{response}` (documented receive outputs).
   - Effect: the EMR is never blocked by a failing check, and variable scope inside `result` doesn't matter.
5. **Generated mock fixtures.** `mock-hie/generate_fixtures.py` builds the Client Registry stubs and bodies from `dha-hie-suite/resources/fixtures.json`. `scripts/check-fixtures.sh` runs it in `--check` mode, so drift is impossible to miss.
6. **Gateway file locations.**
   - The real map and allowlist live in a gitignored directory, `nginx/lab/` (`nginx/lab/lab-vendors.map`, `nginx/lab/lab-allowlist.conf`), which is mounted as a directory. Single-file bind mounts go stale when editors replace the file, and the onboarding flow edits these files.
   - The examples stay at `nginx/lab-vendors.map.example` and `nginx/lab-allowlist.conf.example`.
   - The TLS config is `nginx/lab-gateway-tls.conf.template`, rendered by the nginx image's envsubst. Vendor routing is shared through `nginx/lab-gateway-vendor.inc` and `nginx/lab-gateway-receive.inc`.
7. **`AUTOMATION_ENABLED` stays unchanged in `docker-compose.yml`.** The docs name `AUTOMATION_API_ENABLED`, but the running instance already serves `/api/rest` (it answered `{"error_code":"204","error_description":"Needs API key header."}`).

Task 8 writes these into the spec.

## Review Focus

These are inputs the spec implies but doesn't test. Each line has a pinning test in the task named.

1. **Real OAuth clients add `grant_type=client_credentials` (and sometimes `scope`) to the token form.** The token check and the mock must accept extra form fields. Pinned in Task 1 (mock smoke) and Task 5 (`AUTH-01 with grant_type → SUCCESS`).
2. **Query encoding varies.** Spaces arrive as `+` or `%20`; parameters can come in any order or with extras (`identification_number` first, `&foo=bar`). Parsing must still find the exact type and number. Pinned in Task 5 (`CR-01 raw query, + encoding, reordered → SUCCESS`).
3. **Lower-cased header names** (HTTP/2 clients, proxies) must still pass the Bearer and facility checks. Pinned in Task 5 (`CR-03 lower-case authorization header → SUCCESS`).
4. **A JSON token body** must produce a warning, not a failure. Pinned in Task 5 (`AUTH-01 JSON body → SUCCESS`).
5. **Upstream down (`mock-hie` stopped).** The session must end in FAILURE with a visible cause and must not hang. Pinned in Task 5 (manual step with `mock-hie` stopped).

---

## File structure

| Path | Responsibility | Task |
|---|---|---|
| `dha-hie-suite/resources/fixtures.json` | Single source of test identities | 1 |
| `mock-hie/generate_fixtures.py` | fixtures.json → generated CR stubs and bodies; `--check` mode | 1 |
| `mock-hie/mappings/token.json` | Token endpoint stubs | 1 |
| `mock-hie/mappings/patients-errors.json` | 401 / 400 / 404 stubs for `/patients` | 1 |
| `mock-hie/mappings/patients-fixtures.json` | Generated: one 200 stub per fixture | 1 |
| `mock-hie/__files/patients/*.json` | Generated client records | 1 |
| `scripts/fake-emr.sh` | Simulated EMR. Modes `mock` (T1), `gateway` (T3), `itb`/`itb-bad` (T5) | 1, 3, 5 |
| `scripts/check-fixtures.sh` | Wrapper: generator `--check` | 1 |
| `docker-compose.yml` | `mock-hie`, `lab-gateway`, aliases | 1, 2, 3 |
| `nginx/lab-gateway.conf` | http-level gateway config and the `:80` server | 3 |
| `nginx/lab-gateway-vendor.inc` | Vendor-host → API-key routing location | 3 |
| `nginx/lab-gateway-receive.inc` | Raw `/itbsrv/api/http/` location | 3 |
| `nginx/lab-vendors.map.example`, `nginx/lab-allowlist.conf.example` | Committed examples | 3 |
| `scripts/init-lab-gateway.sh` | Creates `nginx/lab/` from the examples if missing | 3 |
| `dha-hie-suite/testSuite.xml` | Suite definition | 4 |
| `dha-hie-suite/scriptlets/*.xml` | `normaliseRequest`, `formField`, `emrBaseUrl`, `receiveAndForward`, `checkTokenRequest`, `checkBearer`, `checkPatientQuery` | 4 |
| `dha-hie-suite/testCases/*.xml` | `AUTH-01`, `CR-01`, `CR-02`, `CR-03` | 4 |
| `scripts/validate-suite.sh` | XSD validation of suite XML | 4 |
| `scripts/package-suite.sh` | Clean suite zip | 4 |
| `dha-hie-suite.zip` | Packaged suite (committed, as for the existing suites) | 4, 5 |
| `scripts/fake-emr.env.example` | Env template for e2e runs | 5 |
| `dha-hie-suite/README.md` | Setup, run and onboarding guide | 5, 6, 7 |
| `docker-compose.public.yml` | Internet-facing overlay | 7 |
| `nginx/lab-gateway-tls.conf.template` | TLS servers (vendor wildcard and UI host) | 7 |
| `.gitignore` | `nginx/lab/`, `nginx/certs/`, `scripts/.fake-emr.env`, `.cache/` | 3, 4, 5, 7 |

---

### Task 1: Mock HIE (WireMock) with generated Client Registry fixtures

**Files:**
- Create: `dha-hie-suite/resources/fixtures.json`
- Create: `mock-hie/generate_fixtures.py`
- Create: `mock-hie/mappings/token.json`
- Create: `mock-hie/mappings/patients-errors.json`
- Generate: `mock-hie/mappings/patients-fixtures.json`, `mock-hie/__files/patients/*.json`
- Create: `scripts/fake-emr.sh` (mode `mock`)
- Create: `scripts/check-fixtures.sh`
- Modify: `docker-compose.yml` (add the `mock-hie` service after the `hapi-fhir-kenyaitb` block)

**Interfaces:**
- Produces, for later tasks:
  - `fixtures.json` schema: `{nationalId: Client, alternates: Client[6], notFound: {type, number}}`, where `Client = {type, number, crId, firstName, lastName, gender, dateOfBirth, displayName, withDependants?}`.
  - Mock base URL `http://mock-hie:8080/uat-middleware/api/v1` (in-network) and `http://localhost:8095/uat-middleware/api/v1` (host).
  - `scripts/fake-emr.sh` helpers `check`, `status_of`, `fx`, `summary`, the `TOKEN` constant, and the mode dispatch `case "$1"`.

- [ ] **Step 1: Write the fixtures source**

Create `dha-hie-suite/resources/fixtures.json`:

```json
{
  "nationalId": {
    "type": "National ID", "number": "90000001", "crId": "CR-MOCK-1000001",
    "firstName": "Test", "lastName": "Mock-NationalID", "gender": "female",
    "dateOfBirth": "1990-01-01", "displayName": "Test Mock-NationalID", "withDependants": true
  },
  "alternates": [
    {"type": "ClientRegistry ID", "number": "CR-MOCK-1000002", "crId": "CR-MOCK-1000002",
     "firstName": "Test", "lastName": "Mock-ClientRegistryID", "gender": "male",
     "dateOfBirth": "1985-02-02", "displayName": "Test Mock-ClientRegistryID"},
    {"type": "Birth Certificate", "number": "BC-MOCK-1000003", "crId": "CR-MOCK-1000003",
     "firstName": "Test", "lastName": "Mock-BirthCertificate", "gender": "female",
     "dateOfBirth": "2020-03-03", "displayName": "Test Mock-BirthCertificate"},
    {"type": "Birth Notification", "number": "BN-MOCK-1000004", "crId": "CR-MOCK-1000004",
     "firstName": "Test", "lastName": "Mock-BirthNotification", "gender": "male",
     "dateOfBirth": "2025-04-04", "displayName": "Test Mock-BirthNotification"},
    {"type": "Alien ID", "number": "AL-MOCK-1000005", "crId": "CR-MOCK-1000005",
     "firstName": "Test", "lastName": "Mock-AlienID", "gender": "female",
     "dateOfBirth": "1979-05-05", "displayName": "Test Mock-AlienID"},
    {"type": "Refugee ID", "number": "RF-MOCK-1000006", "crId": "CR-MOCK-1000006",
     "firstName": "Test", "lastName": "Mock-RefugeeID", "gender": "male",
     "dateOfBirth": "1995-06-06", "displayName": "Test Mock-RefugeeID"},
    {"type": "Mandate Number", "number": "MN-MOCK-1000007", "crId": "CR-MOCK-1000007",
     "firstName": "Test", "lastName": "Mock-MandateNumber", "gender": "female",
     "dateOfBirth": "1988-07-07", "displayName": "Test Mock-MandateNumber"}
  ],
  "notFound": {"type": "National ID", "number": "00000000"}
}
```

- [ ] **Step 2: Write the failing smoke test (`scripts/fake-emr.sh`, mode `mock`)**

Create `scripts/fake-emr.sh` and run `chmod +x scripts/fake-emr.sh`. It must stay bash-3.2 compatible (macOS): no associative arrays.

```bash
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
      -w '\n%{http_code}')
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
```

- [ ] **Step 3: Run it to verify it fails**

Run: `scripts/fake-emr.sh mock`
Expected: every check prints `FAIL`. Status checks show `got '000'` (connection refused on :8095) and body checks show `got ''`. The run ends `0 passed, 27 failed` with a non-zero exit.

- [ ] **Step 4: Write the static stubs**

Create `mock-hie/mappings/token.json`:

```json
{
  "mappings": [
    {
      "name": "token: form body with non-empty client_id and client_secret",
      "priority": 1,
      "request": {
        "method": "POST",
        "urlPath": "/uat-middleware/api/v1/tenants/token",
        "headers": { "Content-Type": { "contains": "application/x-www-form-urlencoded" } },
        "bodyPatterns": [
          { "matches": "^(.*&)?client_id=[^&]+(&.*)?$" },
          { "matches": "^(.*&)?client_secret=[^&]+(&.*)?$" }
        ]
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "jsonBody": {
          "access_token": "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ0ZW5hbnRfaWQiOiJpdGItbW9jay10ZW5hbnQiLCJmYWNpbGl0eV9pZCI6IkZJRC00Ny0xMTUzMDctOCIsImZhY2lsaXR5X2lkX3R5cGUiOiJmci1jb2RlIiwiaXNzIjoibW9jay1oaWUiLCJzdWIiOiJpdGItdGVzdC1jbGllbnQifQ.",
          "expires_in": 3600,
          "token_type": "Bearer"
        }
      }
    },
    {
      "name": "token: JSON body with non-empty client_id and client_secret",
      "priority": 1,
      "request": {
        "method": "POST",
        "urlPath": "/uat-middleware/api/v1/tenants/token",
        "headers": { "Content-Type": { "contains": "application/json" } },
        "bodyPatterns": [
          { "matchesJsonPath": { "expression": "$.client_id", "matches": ".+" } },
          { "matchesJsonPath": { "expression": "$.client_secret", "matches": ".+" } }
        ]
      },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "jsonBody": {
          "access_token": "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ0ZW5hbnRfaWQiOiJpdGItbW9jay10ZW5hbnQiLCJmYWNpbGl0eV9pZCI6IkZJRC00Ny0xMTUzMDctOCIsImZhY2lsaXR5X2lkX3R5cGUiOiJmci1jb2RlIiwiaXNzIjoibW9jay1oaWUiLCJzdWIiOiJpdGItdGVzdC1jbGllbnQifQ.",
          "expires_in": 3600,
          "token_type": "Bearer"
        }
      }
    },
    {
      "name": "token: anything else",
      "priority": 9,
      "request": { "method": "POST", "urlPath": "/uat-middleware/api/v1/tenants/token" },
      "response": {
        "status": 400,
        "headers": { "Content-Type": "application/json" },
        "jsonBody": { "error": "Bad Request", "message": "client_id and client_secret are required" }
      }
    }
  ]
}
```

Create `mock-hie/mappings/patients-errors.json`. The 401 and 404 shapes are **assumed** (spec §5); confirm them against UAT later.

```json
{
  "mappings": [
    {
      "name": "patients: no Authorization header",
      "priority": 1,
      "request": {
        "method": "GET", "urlPath": "/uat-middleware/api/v1/patients",
        "headers": { "Authorization": { "absent": true } }
      },
      "response": {
        "status": 401, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 401, "details": ["A Bearer token is required"], "error": "Unauthorized",
                      "message": "Missing or invalid Authorization header", "source": "mock-hie" }
      }
    },
    {
      "name": "patients: Authorization is not a Bearer JWT",
      "priority": 1,
      "request": {
        "method": "GET", "urlPath": "/uat-middleware/api/v1/patients",
        "headers": { "Authorization": { "doesNotMatch": "^Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$" } }
      },
      "response": {
        "status": 401, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 401, "details": ["A Bearer token is required"], "error": "Unauthorized",
                      "message": "Missing or invalid Authorization header", "source": "mock-hie" }
      }
    },
    {
      "name": "patients: missing identification_type",
      "priority": 2,
      "request": {
        "method": "GET", "urlPath": "/uat-middleware/api/v1/patients",
        "queryParameters": { "identification_type": { "absent": true } }
      },
      "response": {
        "status": 400, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 400, "details": ["identification_type is required"], "error": "Bad Request",
                      "message": "Missing query parameters or invalid request input", "source": "mock-hie" }
      }
    },
    {
      "name": "patients: missing identification_number",
      "priority": 2,
      "request": {
        "method": "GET", "urlPath": "/uat-middleware/api/v1/patients",
        "queryParameters": { "identification_number": { "absent": true } }
      },
      "response": {
        "status": 400, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 400, "details": ["identification_number is required"], "error": "Bad Request",
                      "message": "Missing query parameters or invalid request input", "source": "mock-hie" }
      }
    },
    {
      "name": "patients: identification_type not in the API enum",
      "priority": 3,
      "request": {
        "method": "GET", "urlPath": "/uat-middleware/api/v1/patients",
        "queryParameters": { "identification_type": {
          "doesNotMatch": "^(National ID|ClientRegistry ID|Birth Notification|Birth Certificate|Alien ID|Refugee ID|Mandate Number)$" } }
      },
      "response": {
        "status": 400, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 400, "details": ["identification_type is not a supported value"], "error": "Bad Request",
                      "message": "Missing query parameters or invalid request input", "source": "mock-hie" }
      }
    },
    {
      "name": "patients: no matching client",
      "priority": 9,
      "request": { "method": "GET", "urlPath": "/uat-middleware/api/v1/patients" },
      "response": {
        "status": 404, "headers": { "Content-Type": "application/json" },
        "jsonBody": { "code": 404, "details": ["No client matches the given identification"], "error": "Not Found",
                      "message": "Client not found", "source": "mock-hie" }
      }
    }
  ]
}
```

- [ ] **Step 5: Write the fixture generator**

Create `mock-hie/generate_fixtures.py`:

```python
#!/usr/bin/env python3
"""Generate mock-hie Client Registry stubs from the suite's fixtures.json.

dha-hie-suite/resources/fixtures.json is the single source of test identities:
the ITB test cases tell testers which numbers to search for, and this script
makes mock-hie answer for exactly those numbers. Run with --check to fail
(exit 1) when the committed mock files are out of date.

Usage: generate_fixtures.py <fixtures.json> <mock-hie dir> [--check]
"""
import argparse
import json
import re
import sys
from pathlib import Path

BASE_PATH = "/uat-middleware/api/v1"
ID_TYPES = ["National ID", "ClientRegistry ID", "Birth Notification", "Birth Certificate",
            "Alien ID", "Refugee ID", "Mandate Number"]


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def client_record(c):
    dependants = []
    if c.get("withDependants"):
        dependants = [{
            "date_added": "2026-01-01",
            "relationship": "child",
            "total": 1,
            "result": [{
                "id": c["crId"] + "-D1", "first_name": "Test", "last_name": c["lastName"] + "-Dependant",
                "gender": "male", "date_of_birth": "2022-05-05",
                "identification_type": "Birth Certificate", "identification_number": "BC-MOCK-DEP-0001",
            }],
        }]
    return {
        "id": c["crId"], "resourceType": "Patient", "title": "",
        "first_name": c["firstName"], "middle_name": "Mock", "last_name": c["lastName"],
        "gender": c["gender"], "date_of_birth": c["dateOfBirth"], "place_of_birth": "Nairobi",
        "deceased_datetime": "", "civil_status": "Single", "citizenship": "Kenyan",
        "phone": "+254700000000", "kra_pin": "",
        "identification_type": c["type"], "identification_number": c["number"],
        "id_serial": "", "identification_residence": "",
        "country": "Kenya", "county": "Nairobi", "sub_county": "Westlands", "ward": "Parklands",
        "village_estate": "Test Estate", "city": "Nairobi", "building_house_no": "1",
        "postal_address": "", "zip_code": "", "province_state_country": "", "latitude": "", "longitude": "",
        "employment_type": "", "employer_name": "", "employer_pin": "", "domestic_worker_type": "",
        "gross_income": 0, "gross_income_currency": "KES",
        "learning_institution_code": "", "learning_institution_name": "", "grade_level": "",
        "admission_number": "", "admission_remand_number": "",
        "disability_category": "", "disability_subcategory": "", "disability_cause": "",
        "agent_id": "", "is_agent": 0, "preferred_primary_care_network": "",
        "meta": {"creationTime": "2026-01-01T00:00:00Z", "lastUpdated": "2026-01-01T00:00:00Z",
                 "source": "mock-hie", "versionId": "1"},
        "originSystem": {"record_id": c["crId"], "system": "mock-hie"},
        "other_identifications": [],
        "dependants": dependants,
        "unconfirmed_dependants": [],
    }


def validate(fixtures):
    clients = [fixtures["nationalId"]] + fixtures["alternates"]
    errors = []
    seen = set()
    for c in clients:
        if c["type"] not in ID_TYPES:
            errors.append(f"{c['number']}: type '{c['type']}' is not in the API enum")
        key = (c["type"], c["number"])
        if key in seen:
            errors.append(f"duplicate fixture {key}")
        seen.add(key)
        if c["number"] == fixtures["notFound"]["number"]:
            errors.append(f"{c['number']} collides with the reserved not-found number")
    if errors:
        sys.exit("invalid fixtures.json:\n  " + "\n  ".join(errors))
    return clients


def render(fixtures):
    """Return {relative path: file content} for every generated file."""
    files = {}
    stubs = []
    for c in validate(fixtures):
        body_file = f"patients/{slug(c['type'])}.json"
        files[f"__files/{body_file}"] = json.dumps(client_record(c), indent=2) + "\n"
        stubs.append({
            "name": f"patients: fixture {c['type']} {c['number']}",
            "priority": 5,
            "request": {
                "method": "GET",
                "urlPath": f"{BASE_PATH}/patients",
                "queryParameters": {
                    "identification_type": {"equalTo": c["type"]},
                    "identification_number": {"equalTo": c["number"]},
                },
            },
            "response": {"status": 200, "headers": {"Content-Type": "application/json"},
                         "bodyFileName": body_file},
        })
    files["mappings/patients-fixtures.json"] = json.dumps({"mappings": stubs}, indent=2) + "\n"
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("fixtures")
    parser.add_argument("mock_dir")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    files = render(json.loads(Path(args.fixtures).read_text()))
    mock_dir = Path(args.mock_dir)
    stale = [p for p, content in files.items()
             if not (mock_dir / p).exists() or (mock_dir / p).read_text() != content]
    if args.check:
        if stale:
            sys.exit("mock-hie fixtures are out of date; run scripts/check-fixtures.sh --write:\n  "
                     + "\n  ".join(stale))
        print(f"mock-hie fixtures up to date ({len(files)} files)")
        return
    for p in stale:
        (mock_dir / p).parent.mkdir(parents=True, exist_ok=True)
        (mock_dir / p).write_text(files[p])
    print(f"wrote {len(stale)} of {len(files)} files")


if __name__ == "__main__":
    main()
```

Create `scripts/check-fixtures.sh` and run `chmod +x scripts/check-fixtures.sh`:

```bash
#!/usr/bin/env bash
# Fails when mock-hie's generated Client Registry fixtures don't match
# dha-hie-suite/resources/fixtures.json. Pass --write to regenerate them.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ "${1:-}" = "--write" ]; then
  python3 -I "$ROOT/mock-hie/generate_fixtures.py" "$ROOT/dha-hie-suite/resources/fixtures.json" "$ROOT/mock-hie"
else
  python3 -I "$ROOT/mock-hie/generate_fixtures.py" "$ROOT/dha-hie-suite/resources/fixtures.json" "$ROOT/mock-hie" --check
fi
```

- [ ] **Step 6: Generate the fixtures and check the drift guard both ways**

Run: `scripts/check-fixtures.sh`
Expected: exit 1, `mock-hie fixtures are out of date`, listing 8 paths.

Run: `scripts/check-fixtures.sh --write && scripts/check-fixtures.sh`
Expected: `wrote 8 of 8 files`, then `mock-hie fixtures up to date (8 files)`.

Run: `ls mock-hie/__files/patients/`
Expected: `alien-id.json birth-certificate.json birth-notification.json clientregistry-id.json mandate-number.json national-id.json refugee-id.json`

- [ ] **Step 7: Add the compose service**

In `docker-compose.yml`, append after the `hapi-fhir-kenyaitb` service, keeping the file's 3-space service and 6-space key indentation:

```yaml

   # ---------------------------
   # MOCK HIE (DHA AfyaConnect middleware stand-in)
   # ---------------------------
   # Replaced by the local UAT services later by changing the ITB domain
   # parameter hieBaseUrl; the test cases never reference this host directly.
   mock-hie:
      image: wiremock/wiremock:3.9.1
      container_name: mock-hie
      restart: unless-stopped
      command: ["--disable-banner"]
      ports:
        - "8095:8080"
      volumes:
        - ./mock-hie:/home/wiremock
```

- [ ] **Step 8: Start the mock and run the smoke test**

Run: `docker compose up -d mock-hie && sleep 5 && scripts/fake-emr.sh mock`
Expected: every line `ok`, ending `27 passed, 0 failed`, exit 0.

If a 400 check returns 200, WireMock matched a fixture or token stub first. Check the `priority` values: lower numbers win.

- [ ] **Step 9: Commit**

```bash
git add dha-hie-suite/resources/fixtures.json mock-hie scripts/fake-emr.sh scripts/check-fixtures.sh docker-compose.yml
git commit -m "Add mock-hie WireMock service with generated Client Registry fixtures"
```

---

### Task 2: Network aliases for the existing services

**Files:**
- Modify: `docker-compose.yml`. Add a `networks.default.aliases` block to `gitb-redis`, `gitb-mysql`, `gitb-srv`, `gitb-ui`, `gitb-ui-proxy` and `hapi-fhir-kenyaitb`.

**Interfaces:**
- Produces: in-network DNS names `lab-service`, `lab-ui`, `lab-ui-proxy`, `lab-db`, `lab-cache` and `lab-fhir`. Tasks 3 and 7 use them.

- [ ] **Step 1: Write the failing check**

Run:
```bash
for a in lab-service lab-ui lab-ui-proxy lab-db lab-cache lab-fhir; do
  docker compose exec -T mock-hie getent hosts "$a" >/dev/null && echo "ok   $a" || echo "FAIL $a"
done
```
Expected: `FAIL` for all six.

- [ ] **Step 2: Add the aliases**

Add this block to each service, using the alias from the table:

| Service | Alias |
|---|---|
| `gitb-redis` | `lab-cache` |
| `gitb-mysql` | `lab-db` |
| `gitb-srv` | `lab-service` |
| `gitb-ui` | `lab-ui` |
| `gitb-ui-proxy` | `lab-ui-proxy` |
| `hapi-fhir-kenyaitb` | `lab-fhir` |

Example for `gitb-srv`, placed after its `ports:` block:

```yaml
      networks:
        default:
          aliases:
            - lab-service
```

Above the `gitb-redis` service, add this comment once:

```yaml
   # Every service also answers to a lab-* network alias (e.g. gitb-srv ->
   # lab-service). New config refers to services by alias; service names stay
   # because the GITB images find each other by their default hostnames.
```

- [ ] **Step 3: Recreate the services and re-run the check**

Run: `docker compose up -d && sleep 10` followed by the loop from Step 1.
Expected: `ok` for all six. The data volumes persist. Containers are recreated only because their network config changed.

Run: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/`
Expected: `200`. The UI still works.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "Add lab-* network aliases to existing compose services"
```

---

### Task 3: lab-gateway (local HTTP)

**Files:**
- Create: `nginx/lab-gateway.conf`, `nginx/lab-gateway-vendor.inc`, `nginx/lab-gateway-receive.inc`
- Create: `nginx/lab-vendors.map.example`, `nginx/lab-allowlist.conf.example`
- Create: `scripts/init-lab-gateway.sh`
- Modify: `docker-compose.yml` (add the `lab-gateway` service), `.gitignore`, `scripts/fake-emr.sh` (add mode `gateway`)

**Interfaces:**
- Consumes: alias `lab-service` (Task 2).
- Produces:
  - Every proxied response carries `X-Lab-Upstream: lab-service`.
  - Every proxied request carries `X-Lab-Original-Uri: <original request URI with query>`.
  - Raw path `http://localhost:9010/itbsrv/api/http/<systemApiKey>/…`.
  - Vendor hosts listed in `nginx/lab/lab-vendors.map`.
  - Unknown host or path: 404 `{"error":"Not Found","message":"Unknown vendor host"}`.

- [ ] **Step 1: Write the failing gateway test**

In `scripts/fake-emr.sh`, add this function above the `case` statement:

```bash
mode_gateway() {
  local unknown_body mapped_headers mapped_body raw_headers codes n429
  unknown_body=$(curl -s "$GATEWAY_URL/tenants/token")
  check "gateway: unknown host -> 404" 404 "$(status_of "$GATEWAY_URL/tenants/token")"
  check "gateway: unknown host body" '{"error":"Not Found","message":"Unknown vendor host"}' "$unknown_body"
  check "gateway: automation API not exposed" 404 "$(status_of "$GATEWAY_URL/api/rest/tests/status")"

  mapped_headers=$(curl -s -D - -o /dev/null "http://test-vendor.lab.localhost:9010/tenants/token")
  check "gateway: mapped vendor host reaches lab-service" yes \
    "$(grep -qi '^X-Lab-Upstream: lab-service' <<<"$mapped_headers" && echo yes || echo no)"
  mapped_body=$(curl -s "http://test-vendor.lab.localhost:9010/api/rest/tests/status")
  check "gateway: vendor host cannot reach the automation API" no \
    "$(grep -q 'Needs API key header' <<<"$mapped_body" && echo yes || echo no)"

  raw_headers=$(curl -s -D - -o /dev/null "$GATEWAY_URL/itbsrv/api/http/NO-SUCH-KEY/patients")
  check "gateway: raw receive path reaches lab-service" yes \
    "$(grep -qi '^X-Lab-Upstream: lab-service' <<<"$raw_headers" && echo yes || echo no)"

  # Parallel, so the burst exceeds 10 r/s + 20 regardless of how fast lab-service answers.
  codes=$(seq 1 60 | xargs -P 30 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
    "http://test-vendor.lab.localhost:9010/rate-limit-probe")
  n429=$(grep -c '^429$' <<<"$codes" || true)
  check "gateway: burst beyond rate limit gets 429" yes "$([ "$n429" -gt 0 ] && echo yes || echo no)"
}
```

Then add `  gateway) mode_gateway ;;` as a branch of the `case`.

Run: `scripts/fake-emr.sh gateway`
Expected: FAIL on all checks (nothing listens on :9010).

- [ ] **Step 2: Confirm `*.localhost` resolves to loopback**

Run: `curl -s -o /dev/null -w '%{remote_ip}\n' http://test-vendor.lab.localhost:9000/`
Expected: `127.0.0.1` or `::1`. curl resolves `*.localhost` itself.

If it doesn't, add `127.0.0.1 test-vendor.lab.localhost` to `/etc/hosts`, then continue.

- [ ] **Step 3: Write the nginx config**

Create `nginx/lab-gateway.conf`:

```nginx
# lab-gateway: the only entry point for EMR traffic (local or vendor-hosted).
#
#  - Vendor hosts listed in /etc/nginx/lab/lab-vendors.map are proxied to
#    lab-service:/itbsrv/api/http/<that system's API key>/<original path>, so
#    an EMR configures a bare, DHA-like base URL.
#  - /itbsrv/api/http/... on any host is proxied unchanged (raw receive URLs).
#  - Everything else is 404. The ITB automation API lives on lab-ui and is
#    never reachable through this gateway.
#  - X-Lab-Original-Uri carries the original URI including the query string:
#    the ITB docs don't say whether a receive step's {request}{uri} keeps it,
#    so the suite's normaliseRequest scriptlet falls back to this header.

# Docker's embedded DNS; needed because proxy_pass targets are variables.
resolver 127.0.0.11 valid=30s ipv6=off;

map $host $vendor_api_key {
    default "";
    include /etc/nginx/lab/lab-vendors.map;
}

limit_req_zone $binary_remote_addr zone=lab:10m rate=10r/s;
limit_req_status 429;

# No Authorization header, no query string and no bodies in the access log.
log_format lab_gateway '$remote_addr [$time_local] "$request_method $uri" $status '
                       '$body_bytes_sent host=$host rt=$request_time';

server {
    listen 80 default_server;
    server_name _;

    access_log /var/log/nginx/access.log lab_gateway;
    default_type application/json;
    client_max_body_size 5m;

    include /etc/nginx/lab/lab-allowlist.conf;
    include /etc/nginx/snippets/lab-gateway-receive.inc;
    include /etc/nginx/snippets/lab-gateway-vendor.inc;
}
```

Create `nginx/lab-gateway-receive.inc`:

```nginx
# Raw ITB receive URLs: /itbsrv/api/http/<system API key>/<uriExtension>.
location /itbsrv/api/http/ {
    limit_req zone=lab burst=20 nodelay;
    set $lab_service lab-service:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Lab-Original-Uri $request_uri;
    proxy_read_timeout 120s;
    add_header X-Lab-Upstream lab-service always;
    proxy_pass http://$lab_service$request_uri;
}
```

Create `nginx/lab-gateway-vendor.inc`:

```nginx
# Clean vendor base URLs: https://<vendor>.<LAB_GATEWAY_DOMAIN>/<path>.
location / {
    if ($vendor_api_key = "") {
        return 404 '{"error":"Not Found","message":"Unknown vendor host"}';
    }
    limit_req zone=lab burst=20 nodelay;
    set $lab_service lab-service:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Lab-Original-Uri $request_uri;
    proxy_read_timeout 120s;
    add_header X-Lab-Upstream lab-service always;
    proxy_pass http://$lab_service/itbsrv/api/http/$vendor_api_key$request_uri;
}
```

Create `nginx/lab-vendors.map.example`:

```nginx
# Vendor host -> ITB system API key, one per line, lower-case host without port:
#   <vendor>.<LAB_GATEWAY_DOMAIN> <system API key from the lab UI>;
# Copied to nginx/lab/lab-vendors.map by scripts/init-lab-gateway.sh.
# Reload after editing: docker compose exec lab-gateway nginx -s reload
#
# acme-emr.lab-itb.example.org 0123456789ABCDEF0123456789ABCDEF;
```

Create `nginx/lab-allowlist.conf.example`:

```nginx
# Optional IP allowlist for lab-gateway. With only comments, every client is
# allowed. To restrict: one "allow <vendor egress IP>;" per line, then "deny all;".
# Copied to nginx/lab/lab-allowlist.conf by scripts/init-lab-gateway.sh.
#
# allow 203.0.113.10;
# deny all;
```

Create `scripts/init-lab-gateway.sh` and run `chmod +x scripts/init-lab-gateway.sh`:

```bash
#!/usr/bin/env bash
# Creates nginx/lab/ (gitignored) from the committed examples, without
# overwriting files that already exist.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/nginx/lab"
for f in lab-vendors.map lab-allowlist.conf; do
  if [ ! -f "$ROOT/nginx/lab/$f" ]; then
    cp "$ROOT/nginx/$f.example" "$ROOT/nginx/lab/$f"
    echo "created nginx/lab/$f"
  fi
done
```

Append to `.gitignore`:

```
nginx/lab/
```

- [ ] **Step 4: Add the compose service**

Append to `docker-compose.yml` after `mock-hie`:

```yaml

   # ---------------------------
   # LAB GATEWAY (EMR entry point)
   # ---------------------------
   # Run scripts/init-lab-gateway.sh once before the first start.
   lab-gateway:
      image: nginx:1.27-alpine
      container_name: lab-gateway
      restart: unless-stopped
      ports:
        - "9010:80"
      volumes:
        - ./nginx/lab-gateway.conf:/etc/nginx/conf.d/default.conf:ro
        - ./nginx/lab-gateway-receive.inc:/etc/nginx/snippets/lab-gateway-receive.inc:ro
        - ./nginx/lab-gateway-vendor.inc:/etc/nginx/snippets/lab-gateway-vendor.inc:ro
        - ./nginx/lab:/etc/nginx/lab:ro
      depends_on:
        gitb-srv:
          condition: service_started
```

- [ ] **Step 5: Add the test vendor entry, start the gateway, run the test**

Run:
```bash
scripts/init-lab-gateway.sh
echo 'test-vendor.lab.localhost TESTKEY-NOT-REAL;' >> nginx/lab/lab-vendors.map
docker compose up -d lab-gateway && sleep 3
docker compose exec lab-gateway nginx -t
scripts/fake-emr.sh gateway
```
Expected:
- `nginx -t` reports `syntax is ok` and `test is successful`.
- The test prints `7 passed, 0 failed`.

`TESTKEY-NOT-REAL` is replaced with a real system API key in Task 6.

- [ ] **Step 6: Commit**

```bash
git add nginx/lab-gateway.conf nginx/lab-gateway-receive.inc nginx/lab-gateway-vendor.inc \
  nginx/lab-vendors.map.example nginx/lab-allowlist.conf.example scripts/init-lab-gateway.sh \
  scripts/fake-emr.sh docker-compose.yml .gitignore
git commit -m "Add lab-gateway nginx service with vendor host mapping and rate limiting"
```

---

### Task 4: DHA HIE test suite (TDL) with validation and packaging

**Files:**
- Create: `scripts/validate-suite.sh`, `scripts/package-suite.sh`
- Create: `dha-hie-suite/testSuite.xml`
- Create: `dha-hie-suite/scriptlets/normaliseRequest.xml`, `formField.xml`, `emrBaseUrl.xml`, `receiveAndForward.xml`, `checkTokenRequest.xml`, `checkBearer.xml`, `checkPatientQuery.xml`
- Create: `dha-hie-suite/testCases/AUTH-01.xml`, `CR-01.xml`, `CR-02.xml`, `CR-03.xml`
- Modify: `.gitignore` (add `.cache/`)

**Interfaces:**
- Consumes:
  - `fixtures.json` (Task 1);
  - the gateway header `X-Lab-Original-Uri` (Task 3);
  - domain parameters `hieBaseUrl` and `labServiceBaseUrl`;
  - system properties `hieClientId`, `hieClientSecret` and `labGatewayBaseUrl`.
- Produces:
  - Test case ids: `AUTH-01`, `CR-01`, `CR-02`, `CR-03`.
  - Suite id `dha-hie-auth-cr`; actors `EMR` (SUT) and `DHA_HIE`.
  - Test case input `skipInteractions` (boolean).
  - Scriptlet calls:

    | Scriptlet | Inputs | Outputs |
    |---|---|---|
    | `receiveAndForward` | `uriExtension`, `method`, `hideRequestBody`, `receiveTimeout` | `request`, `headers`, `queryString`, `upstreamStatus`, `upstreamBody` |
    | `normaliseRequest` | `request` | `headers` (keys `Authorization`, `Content-Type`, `X-Facility-Id`, `X-Facility-Id-Type`, `X-Lab-Original-Uri`), `queryString` |
    | `formField` | `text`, `name` | `value` |
    | `emrBaseUrl` | none | `url` |
    | `checkTokenRequest` | `request`, `headers` | none |
    | `checkBearer` | `headers` | none |
    | `checkPatientQuery` | `request`, `queryString`, `expectedType`, `expectedNumber` | none |

  - Exact failure texts that `fake-emr.sh itb-bad` (Task 5) greps for:
    - `client_secret does not match the secret issued to this system`
    - `identification_type must be exactly`
    - `Authorization header must carry a Bearer JWT`
    - `X-Facility-Id and X-Facility-Id-Type must be sent together`

- [ ] **Step 1: Write the validation script (the failing test)**

Create `scripts/validate-suite.sh` and run `chmod +x scripts/validate-suite.sh`:

```bash
#!/usr/bin/env bash
# Validates every XML file of an ITB test suite against the GITB TDL schema.
# Usage: scripts/validate-suite.sh <suite-dir>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
suite="${1:?usage: validate-suite.sh <suite-dir>}"
xsd_dir="$ROOT/.cache/gitb-xsd"
base="https://raw.githubusercontent.com/ISAITB/gitb-types/master/gitb-types-specs/src/main/resources/schema"
mkdir -p "$xsd_dir"
for f in gitb_tdl.xsd gitb_core.xsd; do
  [ -s "$xsd_dir/$f" ] || curl -sfL "$base/$f" -o "$xsd_dir/$f"
done

expected="testSuite.xml scriptlets/normaliseRequest.xml scriptlets/formField.xml scriptlets/emrBaseUrl.xml
scriptlets/receiveAndForward.xml scriptlets/checkTokenRequest.xml scriptlets/checkBearer.xml
scriptlets/checkPatientQuery.xml testCases/AUTH-01.xml testCases/CR-01.xml testCases/CR-02.xml testCases/CR-03.xml"
missing=0
for f in $expected; do
  [ -f "$suite/$f" ] || { echo "missing $suite/$f"; missing=1; }
done
[ "$missing" -eq 0 ] || exit 1

find "$suite" -name '*.xml' -print0 | xargs -0 xmllint --noout --schema "$xsd_dir/gitb_tdl.xsd"
```

Append `.cache/` to `.gitignore`.

Run: `scripts/validate-suite.sh dha-hie-suite`
Expected: exit 1, with 12 `missing dha-hie-suite/...` lines.

- [ ] **Step 2: Write `dha-hie-suite/testSuite.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuite id="dha-hie-auth-cr" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>DHA HIE APIs: Authentication and Client Registry</gitb:name>
        <gitb:version>0.1.0</gitb:version>
        <gitb:description>Checks that an EMR or facility system calls the DHA AfyaConnect HIE
            token endpoint (POST /tenants/token) and Client Registry lookup (GET /patients) as
            published at https://afyaconnect.dha.go.ke/hie-api. The test bed stands in for the HIE:
            it records each EMR call, forwards it to the configured HIE upstream (hieBaseUrl:
            mock-hie now, local UAT later), relays the reply and then checks the call.</gitb:description>
    </metadata>
    <actors>
        <gitb:actor id="EMR">
            <gitb:name>EMR / facility system</gitb:name>
            <gitb:desc>The system under test: an EMR that obtains an access token and looks up clients in the Client Registry.</gitb:desc>
        </gitb:actor>
        <gitb:actor id="DHA_HIE" hidden="true">
            <gitb:name>DHA HIE (simulated)</gitb:name>
            <gitb:desc>The HIE middleware, played by the test bed and backed by hieBaseUrl.</gitb:desc>
        </gitb:actor>
    </actors>
    <testcase id="AUTH-01"/>
    <testcase id="CR-01"/>
    <testcase id="CR-02"/>
    <testcase id="CR-03"/>
</testsuite>
```

- [ ] **Step 3: Write the helper scriptlets**

`dha-hie-suite/scriptlets/normaliseRequest.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="normaliseRequest" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Normalise an incoming EMR request</gitb:name>
        <gitb:description>Returns the headers the checks need under canonical names, accepting
            lower-cased names too, and the raw query string. The query string comes from the
            request URI when it has one, otherwise from the X-Lab-Original-Uri header added by
            lab-gateway (the ITB docs don't say whether {request}{uri} keeps the query).</gitb:description>
    </metadata>
    <params>
        <var name="request" type="map"/>
    </params>
    <steps>
        <assign to="headers{Authorization}" type="string">if ($request{headers}{Authorization} != '') then $request{headers}{Authorization} else $request{headers}{authorization}</assign>
        <assign to="headers{Content-Type}" type="string">if ($request{headers}{Content-Type} != '') then $request{headers}{Content-Type} else $request{headers}{content-type}</assign>
        <assign to="headers{X-Facility-Id}" type="string">if ($request{headers}{X-Facility-Id} != '') then $request{headers}{X-Facility-Id} else $request{headers}{x-facility-id}</assign>
        <assign to="headers{X-Facility-Id-Type}" type="string">if ($request{headers}{X-Facility-Id-Type} != '') then $request{headers}{X-Facility-Id-Type} else $request{headers}{x-facility-id-type}</assign>
        <assign to="headers{X-Lab-Original-Uri}" type="string">if ($request{headers}{X-Lab-Original-Uri} != '') then $request{headers}{X-Lab-Original-Uri} else $request{headers}{x-lab-original-uri}</assign>
        <assign to="requestUri" type="string">$request{uri}</assign>
        <assign to="queryString" type="string">if (contains($requestUri, '?')) then substring-after($requestUri, '?') else substring-after($headers{X-Lab-Original-Uri}, '?')</assign>
    </steps>
    <output name="headers"/>
    <output name="queryString"/>
</scriptlet>
```

`dha-hie-suite/scriptlets/formField.xml`. The pattern works whether the regex engine uses find or full-match semantics:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="formField" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Read one field from form-encoded text</gitb:name>
        <gitb:description>Returns the decoded value of a field in a k=v&amp;k2=v2 string (a form
            body or a query string), handling both + and %20 for spaces. Empty when absent.</gitb:description>
    </metadata>
    <params>
        <var name="text" type="string"/>
        <var name="name" type="string"/>
    </params>
    <steps>
        <assign to="value" type="string">''</assign>
        <assign to="pattern" type="string">'^(?:.*&amp;)?' || $name || '=([^&amp;]*)(?:&amp;.*)?$'</assign>
        <process id="fieldPresent" handler="RegExpProcessor" operation="check" output="present">
            <input name="input">$text</input>
            <input name="expression">$pattern</input>
        </process>
        <if desc="Decode the field when present">
            <cond>$present</cond>
            <then>
                <process id="fieldMatch" handler="RegExpProcessor" operation="collect" output="matches">
                    <input name="input">$text</input>
                    <input name="expression">$pattern</input>
                </process>
                <process id="fieldDecode" handler="TextProcessor" operation="formDecode" output="value">
                    <input name="input">$matches{0}</input>
                </process>
            </then>
        </if>
    </steps>
    <output name="value"/>
</scriptlet>
```

`dha-hie-suite/scriptlets/emrBaseUrl.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="emrBaseUrl" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>HIE base URL for this EMR</gitb:name>
        <gitb:description>The vendor's clean lab-gateway URL when labGatewayBaseUrl is set,
            otherwise the raw receive base for this system.</gitb:description>
    </metadata>
    <steps>
        <assign to="url" type="string">if ($SYSTEM{labGatewayBaseUrl} != '') then $SYSTEM{labGatewayBaseUrl} else $DOMAIN{labServiceBaseUrl} || '/' || $SYSTEM{apiKey}</assign>
    </steps>
    <output name="url"/>
</scriptlet>
```

- [ ] **Step 4: Write `receiveAndForward.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="receiveAndForward" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Receive an EMR call and forward it to the HIE upstream</gitb:name>
        <gitb:description>Waits for the EMR's call, forwards it unchanged to hieBaseUrl, and
            replies to the EMR with the upstream status, Content-Type and body. Checks are run by
            the caller afterwards, so a failing check never blocks the EMR.</gitb:description>
    </metadata>
    <params>
        <var name="uriExtension" type="string"/>
        <var name="method" type="string"/>
        <var name="hideRequestBody" type="boolean"><value>false</value></var>
        <var name="receiveTimeout" type="number"><value>900000</value></var>
    </params>
    <steps>
        <receive id="emrCall" desc="Wait for the EMR to call the HIE" handler="HttpMessagingV2" timeout="$receiveTimeout" timeoutIsError="true">
            <input name="uriExtension">$uriExtension</input>
            <input name="method">$method</input>
            <input name="showRequestBody">not($hideRequestBody)</input>
            <input name="requestHeadersToHide">"Authorization"</input>
            <result timeout="60000">
                <steps>
                    <call id="incoming" path="scriptlets/normaliseRequest.xml" hidden="true">
                        <input name="request">$emrCall{request}</input>
                    </call>
                    <assign to="upstreamUri" type="string">$DOMAIN{hieBaseUrl} || $uriExtension || (if ($incoming{queryString} = '') then '' else '?' || $incoming{queryString})</assign>
                    <assign to="upstreamHeaders{Accept}" type="string">'application/json'</assign>
                    <if desc="Forward Authorization" hidden="true">
                        <cond>$incoming{headers}{Authorization} != ''</cond>
                        <then><assign to="upstreamHeaders{Authorization}" type="string">$incoming{headers}{Authorization}</assign></then>
                    </if>
                    <if desc="Forward Content-Type" hidden="true">
                        <cond>$incoming{headers}{Content-Type} != ''</cond>
                        <then><assign to="upstreamHeaders{Content-Type}" type="string">$incoming{headers}{Content-Type}</assign></then>
                    </if>
                    <if desc="Forward X-Facility-Id" hidden="true">
                        <cond>$incoming{headers}{X-Facility-Id} != ''</cond>
                        <then><assign to="upstreamHeaders{X-Facility-Id}" type="string">$incoming{headers}{X-Facility-Id}</assign></then>
                    </if>
                    <if desc="Forward X-Facility-Id-Type" hidden="true">
                        <cond>$incoming{headers}{X-Facility-Id-Type} != ''</cond>
                        <then><assign to="upstreamHeaders{X-Facility-Id-Type}" type="string">$incoming{headers}{X-Facility-Id-Type}</assign></then>
                    </if>
                    <if desc="Forward to the HIE upstream">
                        <cond>$method = 'POST'</cond>
                        <then>
                            <send id="upstreamPost" desc="Forward to the HIE upstream" handler="HttpMessagingV2">
                                <input name="uri">$upstreamUri</input>
                                <input name="method">"POST"</input>
                                <input name="version">"1.1"</input>
                                <input name="headers">$upstreamHeaders</input>
                                <input name="body">$emrCall{request}{body}</input>
                                <input name="showRequestBody">not($hideRequestBody)</input>
                                <input name="requestHeadersToHide">"Authorization"</input>
                            </send>
                            <assign to="replyStatus">$upstreamPost{response}{status}</assign>
                            <assign to="replyBody">$upstreamPost{response}{body}</assign>
                            <assign to="replyContentType" type="string">if ($upstreamPost{response}{headers}{Content-Type} != '') then $upstreamPost{response}{headers}{Content-Type} else $upstreamPost{response}{headers}{content-type}</assign>
                        </then>
                        <else>
                            <send id="upstreamGet" desc="Forward to the HIE upstream" handler="HttpMessagingV2">
                                <input name="uri">$upstreamUri</input>
                                <input name="method">$method</input>
                                <input name="version">"1.1"</input>
                                <input name="headers">$upstreamHeaders</input>
                                <input name="requestHeadersToHide">"Authorization"</input>
                            </send>
                            <assign to="replyStatus">$upstreamGet{response}{status}</assign>
                            <assign to="replyBody">$upstreamGet{response}{body}</assign>
                            <assign to="replyContentType" type="string">if ($upstreamGet{response}{headers}{Content-Type} != '') then $upstreamGet{response}{headers}{Content-Type} else $upstreamGet{response}{headers}{content-type}</assign>
                        </else>
                    </if>
                    <assign to="replyHeaders{Content-Type}" type="string">if ($replyContentType != '') then $replyContentType else 'application/json'</assign>
                </steps>
                <output name="status">$replyStatus</output>
                <output name="headers">$replyHeaders</output>
                <output name="body">$replyBody</output>
            </result>
        </receive>
        <call id="received" path="scriptlets/normaliseRequest.xml" hidden="true">
            <input name="request">$emrCall{request}</input>
        </call>
    </steps>
    <output name="request">$emrCall{request}</output>
    <output name="headers">$received{headers}</output>
    <output name="queryString">$received{queryString}</output>
    <output name="upstreamStatus">$emrCall{response}{status}</output>
    <output name="upstreamBody">$emrCall{response}{body}</output>
</scriptlet>
```

- [ ] **Step 5: Write the check scriptlets**

`dha-hie-suite/scriptlets/checkTokenRequest.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="checkTokenRequest" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Check a token request</gitb:name>
        <gitb:description>POST /tenants/token: method, content type and credentials. A JSON body
            is a warning only, because the AfyaConnect docs contradict each other on the format.</gitb:description>
    </metadata>
    <params>
        <var name="request" type="map"/>
        <var name="headers" type="map"/>
    </params>
    <steps>
        <assign to="contentType" type="string">lower-case($headers{Content-Type})</assign>
        <assign to="bodyText" type="string">$request{body}</assign>
        <assign to="clientId" type="string">''</assign>
        <assign to="clientSecret" type="string">''</assign>
        <verify id="tokenMethod" handler="StringValidator" desc="Token request uses POST">
            <input name="actual">$request{method}</input>
            <input name="expected">"POST"</input>
        </verify>
        <if desc="Read the credentials from the body">
            <cond>contains($contentType, 'application/x-www-form-urlencoded')</cond>
            <then>
                <call id="formClientId" path="scriptlets/formField.xml" hidden="true">
                    <input name="text">$bodyText</input>
                    <input name="name">'client_id'</input>
                </call>
                <call id="formClientSecret" path="scriptlets/formField.xml" hidden="true">
                    <input name="text">$bodyText</input>
                    <input name="name">'client_secret'</input>
                </call>
                <assign to="clientId" type="string">$formClientId{value}</assign>
                <assign to="clientSecret" type="string">$formClientSecret{value}</assign>
            </then>
            <else>
                <if desc="Accept a JSON body with a warning">
                    <cond>contains($contentType, 'application/json')</cond>
                    <then>
                        <verify id="tokenFormat" handler="ExpressionValidator" desc="Token request body format" level="WARNING">
                            <input name="expression">contains($contentType, 'application/x-www-form-urlencoded')</input>
                            <input name="failureMessage">"JSON body accepted, but the AfyaConnect API spec defines application/x-www-form-urlencoded for POST /tenants/token."</input>
                        </verify>
                        <process id="jsonClientId" handler="JsonPathProcessor" operation="process" output="clientId">
                            <input name="content">$bodyText</input>
                            <input name="expression">"$.client_id"</input>
                        </process>
                        <process id="jsonClientSecret" handler="JsonPathProcessor" operation="process" output="clientSecret">
                            <input name="content">$bodyText</input>
                            <input name="expression">"$.client_secret"</input>
                        </process>
                    </then>
                    <else>
                        <verify id="tokenContentType" handler="ExpressionValidator" desc="Token request Content-Type">
                            <input name="expression">false()</input>
                            <input name="failureMessage">"Unsupported Content-Type '" || $headers{Content-Type} || "'; POST /tenants/token expects application/x-www-form-urlencoded."</input>
                        </verify>
                    </else>
                </if>
            </else>
        </if>
        <verify id="tokenClientIdPresent" handler="ExpressionValidator" desc="client_id is sent">
            <input name="expression">string($clientId) != ''</input>
            <input name="failureMessage">"client_id is missing from the token request."</input>
        </verify>
        <verify id="tokenClientId" handler="StringValidator" desc="client_id matches this system">
            <input name="actual">$clientId</input>
            <input name="expected">$SYSTEM{hieClientId}</input>
            <input name="failureMessage">"client_id does not match the client_id issued to this system."</input>
        </verify>
        <verify id="tokenClientSecret" handler="ExpressionValidator" desc="client_secret matches this system">
            <input name="expression">string($clientSecret) != '' and string($clientSecret) = $SYSTEM{hieClientSecret}</input>
            <input name="failureMessage">"client_secret does not match the secret issued to this system."</input>
        </verify>
    </steps>
</scriptlet>
```

`dha-hie-suite/scriptlets/checkBearer.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="checkBearer" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Check the Bearer token and facility headers</gitb:name>
        <gitb:description>Authorization must be a Bearer JWT. X-Facility-Id and
            X-Facility-Id-Type are optional, but must be sent together, with type fr-code.</gitb:description>
    </metadata>
    <params>
        <var name="headers" type="map"/>
    </params>
    <steps>
        <assign to="auth" type="string">$headers{Authorization}</assign>
        <assign to="fid" type="string">$headers{X-Facility-Id}</assign>
        <assign to="fidType" type="string">$headers{X-Facility-Id-Type}</assign>
        <verify id="bearerFormat" handler="ExpressionValidator" desc="Authorization carries a Bearer JWT">
            <input name="expression">matches($auth, '^Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$')</input>
            <input name="failureMessage">"Authorization header must carry a Bearer JWT (header.payload.signature)."</input>
        </verify>
        <verify id="facilityPair" handler="ExpressionValidator" desc="Facility headers are sent together">
            <input name="expression">($fid = '' and $fidType = '') or ($fid != '' and $fidType != '')</input>
            <input name="failureMessage">"X-Facility-Id and X-Facility-Id-Type must be sent together; the HIE ignores one without the other."</input>
        </verify>
        <verify id="facilityType" handler="ExpressionValidator" desc="X-Facility-Id-Type is fr-code">
            <input name="expression">$fidType = '' or $fidType = 'fr-code'</input>
            <input name="failureMessage">"X-Facility-Id-Type must be fr-code (the only supported type) but was '" || $fidType || "'."</input>
        </verify>
    </steps>
</scriptlet>
```

`dha-hie-suite/scriptlets/checkPatientQuery.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<scriptlet id="checkPatientQuery" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>Check a Client Registry lookup</gitb:name>
        <gitb:description>GET /patients with both query parameters, identification_type from the
            API enum and equal to the expected type, and the expected number.</gitb:description>
    </metadata>
    <params>
        <var name="request" type="map"/>
        <var name="queryString" type="string"/>
        <var name="expectedType" type="string"/>
        <var name="expectedNumber" type="string"/>
    </params>
    <steps>
        <verify id="lookupMethod" handler="StringValidator" desc="Lookup uses GET">
            <input name="actual">$request{method}</input>
            <input name="expected">"GET"</input>
        </verify>
        <call id="queryType" path="scriptlets/formField.xml" hidden="true">
            <input name="text">$queryString</input>
            <input name="name">'identification_type'</input>
        </call>
        <call id="queryNumber" path="scriptlets/formField.xml" hidden="true">
            <input name="text">$queryString</input>
            <input name="name">'identification_number'</input>
        </call>
        <assign to="idType" type="string">$queryType{value}</assign>
        <assign to="idNumber" type="string">$queryNumber{value}</assign>
        <verify id="typePresent" handler="ExpressionValidator" desc="identification_type is sent">
            <input name="expression">$idType != ''</input>
            <input name="failureMessage">"identification_type query parameter is missing."</input>
        </verify>
        <verify id="typeInEnum" handler="ExpressionValidator" desc="identification_type is an API enum value">
            <input name="expression">$idType = ('National ID', 'ClientRegistry ID', 'Birth Notification', 'Birth Certificate', 'Alien ID', 'Refugee ID', 'Mandate Number')</input>
            <input name="failureMessage">"identification_type must be exactly '" || $expectedType || "' (API enum spelling) but was '" || $idType || "'."</input>
        </verify>
        <verify id="typeExpected" handler="ExpressionValidator" desc="identification_type is the one the tester was asked to use">
            <input name="expression">$idType = $expectedType</input>
            <input name="failureMessage">"identification_type must be exactly '" || $expectedType || "' but was '" || $idType || "'."</input>
        </verify>
        <verify id="numberExpected" handler="ExpressionValidator" desc="identification_number is the one the tester was asked to use">
            <input name="expression">$idNumber = $expectedNumber</input>
            <input name="failureMessage">"identification_number must be '" || $expectedNumber || "' but was '" || $idNumber || "'."</input>
        </verify>
    </steps>
</scriptlet>
```

- [ ] **Step 6: Write the test cases**

`dha-hie-suite/testCases/AUTH-01.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testcase id="AUTH-01" supportsParallelExecution="false" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>AUTH-01 Obtain access token</gitb:name>
        <gitb:version>0.1.0</gitb:version>
        <gitb:description>The EMR requests an access token with POST /tenants/token using the
            client credentials issued to it.</gitb:description>
    </metadata>
    <actors>
        <gitb:actor id="EMR" role="SUT"/>
        <gitb:actor id="DHA_HIE"/>
    </actors>
    <variables>
        <var name="skipInteractions" type="boolean"><value>false</value></var>
    </variables>
    <steps>
        <call id="base" path="scriptlets/emrBaseUrl.xml" hidden="true"/>
        <interact id="instructions" desc="Instructions" blocking="false">
            <instruct desc="What to do" level="INFO" forceDisplay="true">"Set your EMR's HIE base URL to " || $base{url} || " and its client_id to " || $SYSTEM{hieClientId} || " (use the client_secret you were issued). Then make the EMR request a new access token."</instruct>
        </interact>
        <call id="token" path="scriptlets/receiveAndForward.xml">
            <input name="uriExtension">'/tenants/token'</input>
            <input name="method">'POST'</input>
            <input name="hideRequestBody">true()</input>
        </call>
        <call id="tokenChecks" path="scriptlets/checkTokenRequest.xml">
            <input name="request">$token{request}</input>
            <input name="headers">$token{headers}</input>
        </call>
        <verify id="tokenIssued" handler="StringValidator" desc="The HIE issued a token">
            <input name="actual">$token{upstreamStatus}</input>
            <input name="expected">"200"</input>
        </verify>
        <process id="hasAccessToken" handler="JsonPathProcessor" operation="exists" output="accessTokenPresent">
            <input name="content">$token{upstreamBody}</input>
            <input name="expression">"$.access_token"</input>
        </process>
        <verify id="accessTokenReturned" handler="ExpressionValidator" desc="The reply carries access_token">
            <input name="expression">$accessTokenPresent</input>
        </verify>
    </steps>
    <output>
        <success><default>"The EMR obtained an access token correctly."</default></success>
        <failure><default>"The token request did not conform; see the failed steps."</default></failure>
    </output>
</testcase>
```

`dha-hie-suite/testCases/CR-01.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testcase id="CR-01" supportsParallelExecution="false" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>CR-01 Lookup by National ID (found)</gitb:name>
        <gitb:version>0.1.0</gitb:version>
        <gitb:description>The EMR looks up a known client by National ID with GET /patients and
            shows the returned client.</gitb:description>
    </metadata>
    <imports>
        <artifact name="fixtures" type="binary">resources/fixtures.json</artifact>
    </imports>
    <actors>
        <gitb:actor id="EMR" role="SUT"/>
        <gitb:actor id="DHA_HIE"/>
    </actors>
    <variables>
        <var name="skipInteractions" type="boolean"><value>false</value></var>
    </variables>
    <steps>
        <call id="base" path="scriptlets/emrBaseUrl.xml" hidden="true"/>
        <process id="fixtureType" handler="JsonPathProcessor" operation="process" output="expectedType" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.nationalId.type"</input>
        </process>
        <process id="fixtureNumber" handler="JsonPathProcessor" operation="process" output="expectedNumber" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.nationalId.number"</input>
        </process>
        <process id="fixtureName" handler="JsonPathProcessor" operation="process" output="expectedName" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.nationalId.displayName"</input>
        </process>
        <interact id="instructions" desc="Instructions" blocking="false">
            <instruct desc="What to do" level="INFO" forceDisplay="true">"With your EMR's HIE base URL set to " || $base{url} || ", search the Client Registry for the client with " || $expectedType || " " || $expectedNumber || "."</instruct>
        </interact>
        <call id="lookup" path="scriptlets/receiveAndForward.xml">
            <input name="uriExtension">'/patients'</input>
            <input name="method">'GET'</input>
        </call>
        <call id="bearerChecks" path="scriptlets/checkBearer.xml">
            <input name="headers">$lookup{headers}</input>
        </call>
        <call id="queryChecks" path="scriptlets/checkPatientQuery.xml">
            <input name="request">$lookup{request}</input>
            <input name="queryString">$lookup{queryString}</input>
            <input name="expectedType">$expectedType</input>
            <input name="expectedNumber">$expectedNumber</input>
        </call>
        <verify id="clientFound" handler="StringValidator" desc="The Client Registry returned the client">
            <input name="actual">$lookup{upstreamStatus}</input>
            <input name="expected">"200"</input>
        </verify>
        <interact id="confirm" desc="Confirm the EMR shows the client" skipped="$skipInteractions">
            <instruct desc="Expected client" level="INFO">"Expected client: " || $expectedName</instruct>
            <request name="shown" desc="Does your EMR show this client?" inputType="SELECT_SINGLE" options="yes,no" optionLabels="Yes,No"/>
        </interact>
        <if desc="Check the tester's confirmation">
            <cond>not($skipInteractions)</cond>
            <then>
                <verify id="clientShown" handler="StringValidator" desc="The EMR shows the returned client">
                    <input name="actual">$confirm{shown}</input>
                    <input name="expected">"yes"</input>
                    <input name="failureMessage">"The tester reported that the EMR did not show " || $expectedName || "."</input>
                </verify>
            </then>
        </if>
    </steps>
    <output>
        <success><default>"The EMR looked up the client by National ID correctly."</default></success>
        <failure><default>"The Client Registry lookup did not conform; see the failed steps."</default></failure>
    </output>
</testcase>
```

`dha-hie-suite/testCases/CR-02.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testcase id="CR-02" supportsParallelExecution="false" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>CR-02 Lookup by other identifier types</gitb:name>
        <gitb:version>0.1.0</gitb:version>
        <gitb:description>The EMR looks up one client for each remaining identification_type
            (ClientRegistry ID, Birth Certificate, Birth Notification, Alien ID, Refugee ID,
            Mandate Number), using the exact API enum spelling.</gitb:description>
    </metadata>
    <imports>
        <artifact name="fixtures" type="binary">resources/fixtures.json</artifact>
    </imports>
    <actors>
        <gitb:actor id="EMR" role="SUT"/>
        <gitb:actor id="DHA_HIE"/>
    </actors>
    <variables>
        <var name="skipInteractions" type="boolean"><value>false</value></var>
    </variables>
    <steps>
        <call id="base" path="scriptlets/emrBaseUrl.xml" hidden="true"/>
        <process id="alternateCount" handler="JsonPathProcessor" operation="count" output="alternates" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.alternates[*]"</input>
        </process>
        <foreach counter="i" start="0" end="$alternates" desc="One lookup per identifier type">
            <do>
                <process id="fixtureType" handler="JsonPathProcessor" operation="process" output="expectedType" hidden="true">
                    <input name="content">$fixtures</input>
                    <input name="expression">"$.alternates[" || $i || "].type"</input>
                </process>
                <process id="fixtureNumber" handler="JsonPathProcessor" operation="process" output="expectedNumber" hidden="true">
                    <input name="content">$fixtures</input>
                    <input name="expression">"$.alternates[" || $i || "].number"</input>
                </process>
                <process id="fixtureName" handler="JsonPathProcessor" operation="process" output="expectedName" hidden="true">
                    <input name="content">$fixtures</input>
                    <input name="expression">"$.alternates[" || $i || "].displayName"</input>
                </process>
                <interact id="instructions" desc="Instructions" blocking="false">
                    <instruct desc="What to do" level="INFO" forceDisplay="true">"With your EMR's HIE base URL set to " || $base{url} || ", search the Client Registry for the client with " || $expectedType || " " || $expectedNumber || "."</instruct>
                </interact>
                <call id="lookup" path="scriptlets/receiveAndForward.xml">
                    <input name="uriExtension">'/patients'</input>
                    <input name="method">'GET'</input>
                </call>
                <call id="bearerChecks" path="scriptlets/checkBearer.xml">
                    <input name="headers">$lookup{headers}</input>
                </call>
                <call id="queryChecks" path="scriptlets/checkPatientQuery.xml">
                    <input name="request">$lookup{request}</input>
                    <input name="queryString">$lookup{queryString}</input>
                    <input name="expectedType">$expectedType</input>
                    <input name="expectedNumber">$expectedNumber</input>
                </call>
                <verify id="clientFound" handler="StringValidator" desc="The Client Registry returned the client">
                    <input name="actual">$lookup{upstreamStatus}</input>
                    <input name="expected">"200"</input>
                </verify>
                <interact id="confirm" desc="Confirm the EMR shows the client" skipped="$skipInteractions">
                    <instruct desc="Expected client" level="INFO">"Expected client: " || $expectedName</instruct>
                    <request name="shown" desc="Does your EMR show this client?" inputType="SELECT_SINGLE" options="yes,no" optionLabels="Yes,No"/>
                </interact>
                <if desc="Check the tester's confirmation">
                    <cond>not($skipInteractions)</cond>
                    <then>
                        <verify id="clientShown" handler="StringValidator" desc="The EMR shows the returned client">
                            <input name="actual">$confirm{shown}</input>
                            <input name="expected">"yes"</input>
                            <input name="failureMessage">"The tester reported that the EMR did not show " || $expectedName || "."</input>
                        </verify>
                    </then>
                </if>
            </do>
        </foreach>
    </steps>
    <output>
        <success><default>"The EMR looked up clients by every other identification type correctly."</default></success>
        <failure><default>"At least one lookup did not conform; see the failed steps."</default></failure>
    </output>
</testcase>
```

`dha-hie-suite/testCases/CR-03.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testcase id="CR-03" supportsParallelExecution="false" xmlns="http://www.gitb.com/tdl/v1/" xmlns:gitb="http://www.gitb.com/core/v1/">
    <metadata>
        <gitb:name>CR-03 Client not found</gitb:name>
        <gitb:version>0.1.0</gitb:version>
        <gitb:description>The EMR looks up an unknown National ID. The HIE answers 404, and the
            EMR must tell the user that no client was found, without creating or showing one.</gitb:description>
    </metadata>
    <imports>
        <artifact name="fixtures" type="binary">resources/fixtures.json</artifact>
    </imports>
    <actors>
        <gitb:actor id="EMR" role="SUT"/>
        <gitb:actor id="DHA_HIE"/>
    </actors>
    <variables>
        <var name="skipInteractions" type="boolean"><value>false</value></var>
    </variables>
    <steps>
        <call id="base" path="scriptlets/emrBaseUrl.xml" hidden="true"/>
        <process id="fixtureType" handler="JsonPathProcessor" operation="process" output="expectedType" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.notFound.type"</input>
        </process>
        <process id="fixtureNumber" handler="JsonPathProcessor" operation="process" output="expectedNumber" hidden="true">
            <input name="content">$fixtures</input>
            <input name="expression">"$.notFound.number"</input>
        </process>
        <interact id="instructions" desc="Instructions" blocking="false">
            <instruct desc="What to do" level="INFO" forceDisplay="true">"With your EMR's HIE base URL set to " || $base{url} || ", search the Client Registry for the client with " || $expectedType || " " || $expectedNumber || ". No such client exists."</instruct>
        </interact>
        <call id="lookup" path="scriptlets/receiveAndForward.xml">
            <input name="uriExtension">'/patients'</input>
            <input name="method">'GET'</input>
        </call>
        <call id="bearerChecks" path="scriptlets/checkBearer.xml">
            <input name="headers">$lookup{headers}</input>
        </call>
        <call id="queryChecks" path="scriptlets/checkPatientQuery.xml">
            <input name="request">$lookup{request}</input>
            <input name="queryString">$lookup{queryString}</input>
            <input name="expectedType">$expectedType</input>
            <input name="expectedNumber">$expectedNumber</input>
        </call>
        <verify id="clientNotFound" handler="StringValidator" desc="The Client Registry answered not found">
            <input name="actual">$lookup{upstreamStatus}</input>
            <input name="expected">"404"</input>
        </verify>
        <interact id="confirm" desc="Confirm the EMR handled not found" skipped="$skipInteractions">
            <request name="handled" desc="Did your EMR say no client was found, without creating or showing a client?" inputType="SELECT_SINGLE" options="yes,no" optionLabels="Yes,No"/>
        </interact>
        <if desc="Check the tester's confirmation">
            <cond>not($skipInteractions)</cond>
            <then>
                <verify id="notFoundHandled" handler="StringValidator" desc="The EMR handled not found">
                    <input name="actual">$confirm{handled}</input>
                    <input name="expected">"yes"</input>
                    <input name="failureMessage">"The tester reported that the EMR did not handle the not-found reply correctly."</input>
                </verify>
            </then>
        </if>
    </steps>
    <output>
        <success><default>"The EMR handled an unknown client correctly."</default></success>
        <failure><default>"The not-found lookup did not conform; see the failed steps."</default></failure>
    </output>
</testcase>
```

- [ ] **Step 7: Validate against the XSD**

Run: `scripts/validate-suite.sh dha-hie-suite`
Expected: one `... validates` line for each of the 12 files, exit 0.

If xmllint reports an element ordering error, compare the element order with the XSD in `.cache/gitb-xsd/gitb_tdl.xsd`. In a test case the order is `metadata`, `imports`, `actors`, `variables`, `steps`, `output`.

- [ ] **Step 8: Write the packaging script and package**

Create `scripts/package-suite.sh` and run `chmod +x scripts/package-suite.sh`:

```bash
#!/usr/bin/env bash
# Builds an ITB test suite archive with files at the zip root and no macOS
# metadata (__MACOSX, .DS_Store). The suite README stays out of the archive.
# Usage: scripts/package-suite.sh <suite-dir> [output.zip]
set -euo pipefail
suite_dir="$(cd "${1:?usage: package-suite.sh <suite-dir> [output.zip]}" && pwd)"
out="${2:-$suite_dir.zip}"
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac
rm -f "$out"
(cd "$suite_dir" && zip -q -r -X "$out" . -x '.DS_Store' '*/.DS_Store' '__MACOSX/*' 'README.md')
echo "$out"
```

Run: `scripts/package-suite.sh dha-hie-suite && unzip -l dha-hie-suite.zip`
Expected:
- 13 entries: `testSuite.xml`, `resources/fixtures.json`, 7 `scriptlets/*.xml` and 4 `testCases/*.xml`, plus directory entries.
- No `__MACOSX` and no leading `dha-hie-suite/` folder.

- [ ] **Step 9: Commit**

```bash
git add dha-hie-suite scripts/validate-suite.sh scripts/package-suite.sh dha-hie-suite.zip .gitignore
git commit -m "Add DHA HIE auth and client registry ITB test suite"
```

---

### Task 5: ITB configuration and end-to-end verification

**Files:**
- Modify: `scripts/fake-emr.sh` (modes `itb` and `itb-bad`)
- Create: `scripts/fake-emr.env.example`, `dha-hie-suite/README.md`
- Modify: `.gitignore` (add `scripts/.fake-emr.env`)
- Possibly modify: Task 4 XML, but only to fix behaviour this task uncovers. Re-run Task 4 Step 7 and Step 8 after any edit.

**Interfaces:**
- Consumes: everything from Tasks 1–4.
- Produces:
  - A working DHA HIE specification in the lab UI.
  - A test organisation and system (`fake-emr`).
  - `scripts/.fake-emr.env` (gitignored) with these variables:

    | Variable | Contents |
    |---|---|
    | `ITB_UI_URL` | Lab UI base URL |
    | `LAB_ORG_API_KEY` | Organisation API key |
    | `LAB_SYSTEM_API_KEY` | System API key |
    | `LAB_ACTOR_API_KEY` | Actor API key (optional) |
    | `LAB_CLIENT_ID` | Client ID |
    | `LAB_CLIENT_SECRET` | Client secret |
    | `EMR_BASE_URL` | Optional override |

  - Script knobs: `ONLY=<test case id>` runs one test case; `RESULT_WAIT=<polls>` sets how many 2-second polls to wait (default 60); `SESSION_WARMUP=<seconds>` sets the pause before the EMR call (default 3).

- [ ] **Step 1: Write the end-to-end test modes (the failing test)**

Create `scripts/fake-emr.env.example`:

```bash
# Copy to scripts/.fake-emr.env (gitignored) and fill in from the lab UI.
ITB_UI_URL=http://localhost:9000
LAB_ORG_API_KEY=
LAB_SYSTEM_API_KEY=
# Only needed if session start complains that the actor is ambiguous.
LAB_ACTOR_API_KEY=
LAB_CLIENT_ID=itb-test-client
LAB_CLIENT_SECRET=
# Defaults to the raw receive URL through lab-gateway; set to a vendor host to
# test clean base URLs, e.g. http://test-vendor.lab.localhost:9010
EMR_BASE_URL=
```

Append `scripts/.fake-emr.env` to `.gitignore`.

Add these functions to `scripts/fake-emr.sh` above the `case` statement:

```bash
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
  session=$(start_session "$4")
  if [ -z "$session" ]; then check "$1: session started" yes no; return; fi
  sleep "${SESSION_WARMUP:-3}"
  "$5"
  result=$(wait_result "$session")
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
```

Add these branches to the `case`:

```bash
  itb) mode_itb ;;
  itb-bad) mode_itb_bad ;;
```

Run: `scripts/fake-emr.sh itb`
Expected: exit 2 with `set ITB_UI_URL in .../scripts/.fake-emr.env`. The env file doesn't exist yet.

- [ ] **Step 2: Configure ITB in the lab UI (manual, as administrator at http://localhost:9000)**

Use the domain that holds the existing KPS/KenyaCore specifications. The steps follow the ITB admin guide at https://www.itb.ec.europa.eu/docs/itb-ta/latest/.

1. **Domain parameters** (Domain management → *domain* → Parameters → Create):

   | Name | Kind | Value | Notes |
   |---|---|---|---|
   | `hieBaseUrl` | Simple | `http://mock-hie:8080/uat-middleware/api/v1` | Tick *in tests* |
   | `labServiceBaseUrl` | Simple | `http://localhost:9010/itbsrv/api/http` | Tick *in tests* |

2. **Specification** (Domain → Specifications → Create): name `DHA HIE APIs`. Upload `dha-hie-suite.zip` to it (Test suites → Upload), accepting the new actors `EMR` and `DHA_HIE`.
3. **Custom system properties** (Community management → *community* → Edit custom member properties → System properties):

   | Key | Type | Required | Editable by users | In tests |
   |---|---|---|---|---|
   | `hieClientId` | Simple | yes | no | yes |
   | `hieClientSecret` | Secret | yes | no | yes |
   | `labGatewayBaseUrl` | Simple | no | no | yes |

4. **Test organisation** `IntelliSOFT Fake EMR` with system `fake-emr`. Set the system properties:
   - `hieClientId = itb-test-client`
   - `hieClientSecret` = the output of `openssl rand -hex 16`
   - leave `labGatewayBaseUrl` empty
5. **Conformance statement**: give system `fake-emr` the `DHA HIE APIs` / `EMR` statement.
6. Copy the organisation API key, system API key and (if shown) actor API key into `scripts/.fake-emr.env`, created from the example. Use the same client id and secret as in item 4.

- [ ] **Step 3: Run the happy path**

Run: `scripts/fake-emr.sh itb`
Expected: every line `ok`, ending `9 passed, 0 failed`.

If a case fails, open its session in the lab UI. Common causes:

| Symptom | Cause and fix |
|---|---|
| `TIMEOUT` with the session still waiting at the receive | The EMR call arrived before the session reached the receive. Re-run with `SESSION_WARMUP=6 scripts/fake-emr.sh itb`. |
| CR checks fail with an empty `identification_type` | Neither `{request}{uri}` nor `X-Lab-Original-Uri` reached the scriptlet. Confirm `EMR_BASE_URL` goes through `:9010`, then inspect the receive step's request headers in the report. |
| `client_id is missing` with a correct form body | `<assign type="string">$request{body}</assign>` did not turn the binary body into text. That is the only conversion the plan relies on that the ITB docs don't show. **Stop and report BLOCKED**, attaching the AUTH-01 session report. Don't guess an alternative conversion. |
| Session start returns an error about the actor | Set `LAB_ACTOR_API_KEY`. |

Record anything you changed in the commit message.

- [ ] **Step 4: Run the negative path**

Run: `scripts/fake-emr.sh itb-bad`
Expected: `8 passed, 0 failed`. Each case ends in FAILURE, and its report contains the expected message.

If a result is FAILURE but the message check fails, print `$STATUS_FILE` temporarily (add `cat "$STATUS_FILE"` in `run_case`) and look for the step's failure text.
- If the text is there with different escaping, adjust the grep string. Keep the message distinctive.
- If neither the status JSON nor the XML report carries step messages, **stop and report BLOCKED** with the output. Never delete the message assertion.

- [ ] **Step 5: Upstream-down check (Review Focus 5, manual)**

Run:
```bash
docker compose stop mock-hie
ONLY=CR-01 scripts/fake-emr.sh itb 2>&1 | grep 'CR-01 National ID'
docker compose start mock-hie
```
Expected:
- `FAIL CR-01 National ID: result: expected 'SUCCESS', got 'FAILURE'`. It must not say `TIMEOUT`.
- In the lab UI, the session shows the upstream forwarding step as failed (connection refused).

- [ ] **Step 6: Write `dha-hie-suite/README.md`**

```markdown
# DHA HIE APIs: Authentication and Client Registry (ITB suite)

Conformance tests for EMR/facility systems that call the DHA AfyaConnect HIE
token (`POST /tenants/token`) and Client Registry (`GET /patients`) APIs.
Design: `docs/superpowers/specs/2026-10-14-dha-hie-auth-client-registry-design.md`.

## How it works

EMR → `lab-gateway` (:9010) → ITB receive (`lab-service`) → forwarded to
`hieBaseUrl` (`mock-hie` now, local UAT later). The EMR gets the upstream reply.
ITB then checks the call: credentials, Bearer JWT, facility headers, and the
`identification_type` enum and number.

## One-time setup

1. `scripts/init-lab-gateway.sh && docker compose up -d`
2. In the lab UI (http://localhost:9000), as administrator:
   - Domain parameters: `hieBaseUrl = http://mock-hie:8080/uat-middleware/api/v1`
     and `labServiceBaseUrl = http://localhost:9010/itbsrv/api/http` (both *in tests*).
   - Custom system properties: `hieClientId` (simple), `hieClientSecret` (secret)
     and `labGatewayBaseUrl` (simple, optional). All *in tests*, not editable by users.
   - Specification `DHA HIE APIs`: upload `dha-hie-suite.zip`.
3. For each EMR system: set `hieClientId` and `hieClientSecret` (generate the secret
   with `openssl rand -hex 16`), and give it the `DHA HIE APIs / EMR` conformance statement.

## Running a test as a tester

1. Configure the EMR's HIE base URL with the URL shown in the test's first
   instruction. It is the raw receive URL `http://<host>:9010/itbsrv/api/http/<system API key>`,
   or the vendor's clean URL when `labGatewayBaseUrl` is set. Also configure
   the client_id/secret you were issued.
2. Have the EMR ready, start the test case, then do what the instruction says.
   Each step waits up to 15 minutes for the EMR's call.
3. Answer the confirmation prompt about what the EMR showed.

Test identities are in `resources/fixtures.json`. They are synthetic only.

## Changing test identities

Edit `resources/fixtures.json`, then run `scripts/check-fixtures.sh --write`,
`docker compose restart mock-hie`, `scripts/package-suite.sh dha-hie-suite`,
and upload the zip again.

## Verifying the suite (developers)

    scripts/fake-emr.sh mock       # mock-hie stubs
    scripts/fake-emr.sh gateway    # lab-gateway routing and limits
    scripts/fake-emr.sh itb        # end-to-end, expected SUCCESS
    scripts/fake-emr.sh itb-bad    # end-to-end, expected FAILURE with messages

The `itb` modes need `scripts/.fake-emr.env` (copy `scripts/fake-emr.env.example`).
They start sessions with `skipInteractions=true`, because ITB has no API to
answer interaction prompts.

## Moving to local UAT

Set `hieBaseUrl` to the UAT base URL. Set each system's credentials to the
UAT-issued ones. Replace the identities in `resources/fixtures.json` with UAT
test identities, then repackage and upload. No test-case XML changes are needed.
Confirm the assumed 401/404 shapes against UAT.
```

- [ ] **Step 7: Commit**

```bash
git add scripts/fake-emr.sh scripts/fake-emr.env.example dha-hie-suite .gitignore dha-hie-suite.zip
git commit -m "Add end-to-end fake EMR runs and suite setup guide"
```

---

### Task 6: Clean vendor base URL end-to-end

**Files:**
- Modify (local, gitignored): `nginx/lab/lab-vendors.map`
- Modify: `dha-hie-suite/README.md` (vendor onboarding section)

**Interfaces:**
- Consumes:
  - the `fake-emr` system and its API key (Task 5);
  - the `test-vendor.lab.localhost` map entry (Task 3);
  - `EMR_BASE_URL` override support in `fake-emr.sh` (Task 5).

- [ ] **Step 1: Write the failing run**

Run: `RESULT_WAIT=5 ONLY=AUTH-01 EMR_BASE_URL=http://test-vendor.lab.localhost:9010 scripts/fake-emr.sh itb`
Expected: the three AUTH-01 cases fail with `got 'TIMEOUT'`, and the script stops those sessions. The map still points at `TESTKEY-NOT-REAL`, so the calls never reach the waiting session.

- [ ] **Step 2: Map the test vendor host to the real system key and set the property**

Run:
```bash
. scripts/.fake-emr.env
sed -i '' "s/^test-vendor.lab.localhost .*/test-vendor.lab.localhost $LAB_SYSTEM_API_KEY;/" nginx/lab/lab-vendors.map
docker compose exec lab-gateway nginx -s reload
```
On Linux use `sed -i` instead of `sed -i ''`.

In the lab UI, set the system `fake-emr` property `labGatewayBaseUrl = http://test-vendor.lab.localhost:9010`.

- [ ] **Step 3: Re-run through the clean URL**

Run: `EMR_BASE_URL=http://test-vendor.lab.localhost:9010 scripts/fake-emr.sh itb`
Expected: `9 passed, 0 failed`.

Run: `scripts/fake-emr.sh gateway`
Expected: `7 passed, 0 failed`. The routing checks still pass with a real key.

- [ ] **Step 4: Add vendor onboarding to the README**

Append to `dha-hie-suite/README.md`:

```markdown
## Onboarding an externally hosted (vendor) EMR

Vendors only need outbound HTTPS to the lab gateway; the lab never calls the EMR.

1. In the lab UI, create the vendor's organisation and system and note the system API key.
2. Set the system properties `hieClientId`, `hieClientSecret` and
   `labGatewayBaseUrl = https://<vendor>.<LAB_GATEWAY_DOMAIN>`.
3. Add `<vendor>.<LAB_GATEWAY_DOMAIN> <system API key>;` to `nginx/lab/lab-vendors.map`.
   Optionally add the vendor's egress IP to `nginx/lab/lab-allowlist.conf`
   (`allow <ip>;` lines, then `deny all;`).
4. `docker compose exec lab-gateway nginx -s reload`
5. Send the vendor the base URL and credentials out-of-band.

The vendor tester logs into the lab UI under their organisation and runs the
test cases while operating their EMR. Alternatively, an administrator runs the
sessions on a call with the vendor.

**Revoking a vendor:** remove their map line and reload nginx. If the API key
leaked, also regenerate the system API key in the lab UI.

**Local testing of vendor hosts:** `*.lab.localhost` names resolve to loopback, e.g.
`http://test-vendor.lab.localhost:9010`.
```

- [ ] **Step 5: Commit**

```bash
git add dha-hie-suite/README.md
git commit -m "Document vendor onboarding through lab-gateway clean base URLs"
```

---

### Task 7: Public deployment overlay (TLS)

**Files:**
- Create: `docker-compose.public.yml`, `nginx/lab-gateway-tls.conf.template`
- Modify: `.gitignore` (add `nginx/certs/`), `dha-hie-suite/README.md` (public deployment section)

**Interfaces:**
- Consumes:
  - aliases `lab-service` and `lab-ui-proxy` (Task 2);
  - `nginx/lab-gateway-vendor.inc` and `nginx/lab-gateway-receive.inc` (Task 3).
- Produces:
  - `.env` keys `ITB_UI_HOST` and `LAB_GATEWAY_DOMAIN` (the latter for DNS/docs only).
  - Cert files `nginx/certs/gateway.{crt,key}` (wildcard for vendor hosts) and `nginx/certs/ui.{crt,key}`.

- [ ] **Step 1: Write the failing checks**

Run:
```bash
ITB_UI_HOST=ui.lab.localhost docker compose -f docker-compose.yml -f docker-compose.public.yml config >/dev/null; echo "config exit $?"
curl -sk -o /dev/null -w '%{http_code}\n' https://test-vendor.lab.localhost/tenants/token
```
Expected: the config command fails because the file doesn't exist (non-zero `config exit`), and curl prints `000`.

- [ ] **Step 2: Write the overlay and TLS template**

Create `docker-compose.public.yml`:

```yaml
# Internet-facing overlay: only lab-gateway is published (HTTPS 443). It serves
# vendor EMR hosts and the lab UI host; everything else stays on the internal
# network. Use:
#   docker compose -f docker-compose.yml -f docker-compose.public.yml up -d
# Needs Docker Compose >= 2.24 (!reset / !override), ITB_UI_HOST in .env, and
# certificates in nginx/certs/ (gateway.crt/key: wildcard *.<LAB_GATEWAY_DOMAIN>;
# ui.crt/key: <ITB_UI_HOST>).
services:

   gitb-srv:
      ports: !reset []
      environment:
        - CALLBACK_ROOT_URL=https://${ITB_UI_HOST:?set ITB_UI_HOST in .env}

   gitb-ui:
      environment:
        - TESTBED_HOME_LINK=https://${ITB_UI_HOST}/

   gitb-ui-proxy:
      ports: !reset []

   mock-hie:
      ports: !reset []

   lab-gateway:
      ports: !override
        - "443:443"
      environment:
        - ITB_UI_HOST=${ITB_UI_HOST}
      volumes:
        - ./nginx/lab-gateway-tls.conf.template:/etc/nginx/templates/lab-gateway-tls.conf.template:ro
        - ./nginx/certs:/etc/nginx/certs:ro
```

Create `nginx/lab-gateway-tls.conf.template`. The nginx image renders it into `conf.d/` with envsubst, which only replaces variables defined in the container's environment (`ITB_UI_HOST`). nginx's own `$variables` are left untouched.

```nginx
# Public TLS entry points (docker-compose.public.yml only).

# Declared before the servers that use it.
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

# Vendor EMR hosts: *.<LAB_GATEWAY_DOMAIN> with the wildcard certificate.
server {
    listen 443 ssl default_server;
    server_name _;
    ssl_certificate     /etc/nginx/certs/gateway.crt;
    ssl_certificate_key /etc/nginx/certs/gateway.key;

    access_log /var/log/nginx/access.log lab_gateway;
    default_type application/json;
    client_max_body_size 5m;

    include /etc/nginx/lab/lab-allowlist.conf;
    include /etc/nginx/snippets/lab-gateway-receive.inc;
    include /etc/nginx/snippets/lab-gateway-vendor.inc;
}

# Lab UI for testers, plus raw receive URLs under CALLBACK_ROOT_URL.
server {
    listen 443 ssl;
    server_name ${ITB_UI_HOST};
    ssl_certificate     /etc/nginx/certs/ui.crt;
    ssl_certificate_key /etc/nginx/certs/ui.key;

    access_log /var/log/nginx/access.log lab_gateway;
    client_max_body_size 50m;

    include /etc/nginx/snippets/lab-gateway-receive.inc;

    location / {
        set $lab_ui_proxy lab-ui-proxy:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
        proxy_pass http://$lab_ui_proxy;
    }
}
```

Append `nginx/certs/` to `.gitignore`.

- [ ] **Step 3: Check the merged config**

Run:
```bash
ITB_UI_HOST=ui.lab.localhost docker compose -f docker-compose.yml -f docker-compose.public.yml config --format json \
  | jq '{srv: .services["gitb-srv"].ports, ui: .services["gitb-ui-proxy"].ports, mock: .services["mock-hie"].ports,
         gw: [.services["lab-gateway"].ports[].published], cb: [.services["gitb-srv"].environment.CALLBACK_ROOT_URL]}'
```
Expected:
```json
{"srv": null, "ui": null, "mock": null, "gw": ["443"], "cb": ["https://ui.lab.localhost"]}
```
`null` or `[]` both mean "not published".

- [ ] **Step 4: Verify TLS with self-signed certificates (only lab-gateway is recreated)**

Run:
```bash
mkdir -p nginx/certs
openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj '/CN=*.lab.localhost' \
  -addext 'subjectAltName=DNS:*.lab.localhost' -keyout nginx/certs/gateway.key -out nginx/certs/gateway.crt
openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj '/CN=ui.lab.localhost' \
  -addext 'subjectAltName=DNS:ui.lab.localhost' -keyout nginx/certs/ui.key -out nginx/certs/ui.crt
ITB_UI_HOST=ui.lab.localhost docker compose -f docker-compose.yml -f docker-compose.public.yml up -d --no-deps lab-gateway
sleep 3
curl -sk -D - -o /dev/null https://test-vendor.lab.localhost/tenants/token | grep -i '^X-Lab-Upstream'
curl -sk -o /dev/null -w '%{http_code}\n' https://unknown.lab.localhost/tenants/token
curl -sk -o /dev/null -w '%{http_code}\n' https://ui.lab.localhost/
```
Expected:
- `X-Lab-Upstream: lab-service`
- `404` for the unknown host
- `200` for the UI host

Restore the local gateway:

```bash
docker compose up -d --no-deps lab-gateway && scripts/fake-emr.sh gateway
```
Expected: `7 passed, 0 failed`.

- [ ] **Step 5: Add the public deployment section to the README**

Append to `dha-hie-suite/README.md`:

```markdown
## Public deployment

1. DNS: a wildcard record `*.<LAB_GATEWAY_DOMAIN>` and a record for `<ITB_UI_HOST>`,
   both pointing to the host.
2. `.env`: `ITB_UI_HOST=<ui host>` and `LAB_GATEWAY_DOMAIN=<gateway domain>`.
3. Certificates in `nginx/certs/` (gitignored):
   - `gateway.crt`/`gateway.key`: wildcard `*.<LAB_GATEWAY_DOMAIN>`. Let's Encrypt needs a DNS-01 challenge for this.
   - `ui.crt`/`ui.key`: `<ITB_UI_HOST>`.
4. `docker compose -f docker-compose.yml -f docker-compose.public.yml up -d`
5. Set the domain parameter `labServiceBaseUrl = https://<ITB_UI_HOST>/itbsrv/api/http`.

Only port 443 is published. The ITB engine, the automation API, mock-hie and the
UI proxy are internal. Use synthetic fixtures only, because the endpoint is
internet-facing.

**Temporary sharing without a public host:** expose only `lab-gateway:9010`
through a tunnel (cloudflared/ngrok), set `labServiceBaseUrl` to
`<tunnel URL>/itbsrv/api/http`, and have the vendor use the raw receive URL.
This is not for ongoing use.
```

- [ ] **Step 6: Commit**

```bash
git add docker-compose.public.yml nginx/lab-gateway-tls.conf.template .gitignore dha-hie-suite/README.md
git commit -m "Add public TLS overlay for lab-gateway and lab UI"
```

---

### Task 8: Fold deviations into the spec and run the final verification

**Files:**
- Modify: `docs/superpowers/specs/2026-10-14-dha-hie-auth-client-registry-design.md`

- [ ] **Step 1: Amend the spec**

Make these edits:

| Spec section | Change |
|---|---|
| §3.1 | Replace the `nginx/lab-gateway-tls.conf`, `nginx/lab-vendors.map.example` and `nginx/lab-allowlist.conf.example` rows with the files actually created: `lab-gateway-tls.conf.template`, the `.inc` snippets, `nginx/lab/` (gitignored), and `scripts/init-lab-gateway.sh`, `validate-suite.sh` and `check-fixtures.sh`. Also add `mock-hie/generate_fixtures.py`. |
| §3.2 | Add the domain parameter `labServiceBaseUrl`. The "Not set" URL row becomes `$DOMAIN{labServiceBaseUrl}/$SYSTEM{apiKey}`. |
| §4 | Note that checks run after the reply (deviation 4), and add the `normaliseRequest`, `formField` and `emrBaseUrl` scriptlets. |
| §6 | Interaction prompts are skipped through the `skipInteractions` input (deviation 1). Also list the added Review Focus runs. |
| §7.2 | `X-Lab-Original-Uri` and `X-Lab-Upstream` headers; the `nginx/lab/` paths; reload instructions unchanged. |
| §10 | Add the risk "query string not documented on `{request}{uri}`", mitigated by `X-Lab-Original-Uri`. |

- [ ] **Step 2: Final verification, all suites green**

Run:
```bash
scripts/check-fixtures.sh
scripts/validate-suite.sh dha-hie-suite
scripts/fake-emr.sh mock
scripts/fake-emr.sh gateway
scripts/fake-emr.sh itb
scripts/fake-emr.sh itb-bad
EMR_BASE_URL=http://test-vendor.lab.localhost:9010 scripts/fake-emr.sh itb
git status --short
```
Expected:
- Each script ends with `0 failed` or `validates`.
- The existing KPS suite still runs (spot-check one session in the lab UI).
- `git status` shows only the spec change. No `nginx/lab/`, certs or `.fake-emr.env` appear.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-14-dha-hie-auth-client-registry-design.md
git commit -m "Spec: record implementation deviations for DHA HIE suite"
```
