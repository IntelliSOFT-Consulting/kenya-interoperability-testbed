# DHA HIE APIs — Authentication & Client Registry Conformance Suite

**Date:** 2026-10-14
**Status:** Draft for review
**Scope:** First increment of testing Kenya DHA AfyaConnect HIE APIs in the Kenya Interoperability Testbed (ITB).

## 1. Purpose

Extend the testbed so that **facility/EMR systems that consume DHA AfyaConnect HIE APIs** can be conformance-tested. This increment covers:

1. Authentication & Authorization — https://afyaconnect.dha.go.ke/hie-api/auth
2. Client Registry — https://afyaconnect.dha.go.ke/hie-api/hieRegistry/client-registry

The DHA services are provided by a **mock** for now. Later, a local UAT environment running the real services will replace the mock. **That switch must need configuration changes only, with no edits to the test cases.**

### Decisions made during brainstorming

| Topic | Decision |
|---|---|
| System Under Test | The **EMR/facility client** that calls DHA APIs. ITB plays DHA. |
| How the EMR is triggered | **Tester-driven** first: ITB `interact` steps tell the tester what to do in the EMR. Automated triggering comes later and is out of scope here. The `fake-emr.sh` script is the seed for it. |
| Architecture | **ITB as a validating gateway** in front of a separate upstream (mock now, UAT later). |
| Mock technology | **WireMock** (declarative stubs and fixture files). |

## 2. Source contract (from AfyaConnect public docs, retrieved 2026-10-14)

UAT base URL: `https://ilm-dev.dha.go.ke/uat-middleware/api/v1`

### 2.1 Get access token — `POST /tenants/token`

- No auth is required.
- Body fields: `client_id` (required) and `client_secret` (required).
- The spec says the content type is `application/x-www-form-urlencoded`. The doc code samples send JSON, so the docs contradict each other.
- 200 response: `{"access_token": string, "expires_in": integer, "token_type": string}`.
- 400 and 500 responses: `{"error": string, "message": string}`. Documented example: `"token missing required tenant_id claim"`.
- No refresh token is documented.

### 2.2 Using the token

- Every other endpoint needs `Authorization: Bearer <JWT>`.
- JWT claims: `tenant_id`, plus `facility_id` and `facility_id_type` on facility-scoped tokens.
- Multitenant or service-account tokens carry no facility claim. Callers using them must send `X-Facility-Id` and `X-Facility-Id-Type`; `fr-code` is the only supported type.
  - If both headers are sent, they override the token claim.
  - If only one is sent, the pair is ignored and the claim is used.

### 2.3 Client Registry — `GET /patients`

- Query parameters, both required:
  - `identification_number`
  - `identification_type`, one of: `National ID`, `ClientRegistry ID`, `Birth Notification`, `Birth Certificate`, `Alien ID`, `Refugee ID`, `Mandate Number`.
- 200 response: a flat custom JSON client record, not FHIR. Fields include:
  - Identity and biodata: `id`, `resourceType`, `first_name`, `middle_name`, `last_name`, `gender`, `date_of_birth`, `identification_type`, `identification_number`, `other_identifications[]`
  - Nested objects: `meta{}`, `originSystem{}`, `dependants[]`, `unconfirmed_dependants[]`
  - Address, employment and disability fields
- 400 response: `{code, details[], error, message, source}`.
- **Gaps in the docs:** no "not found" response and no 401 response are documented. The narrative guide lists identifier spellings that differ from the API enum (e.g. `NATIONAL ID`, `CR ID`, `SHA NUMBER`, `TEMPORARY ID`). **This spec treats the API enum as the contract.**

## 3. Architecture

```
                         docker network (compose)
EMR (SUT) ──HTTP──► gitb-srv  HttpMessagingV2 receive ──forward──► mock-dha (WireMock)
  DHA base URL =        validates the request                    /uat-middleware/api/v1/...
  http://<itb-host>:8080/itbsrv/api/http/<systemApiKey>
                        relays the upstream response to the EMR
                        reports results in gitb-ui
                              │
                              └─ upstream = $DOMAIN{dhaBaseUrl}
                                 now:   http://mock-dha:8080/uat-middleware/api/v1
                                 later: local UAT base URL
```

**Division of responsibility**

- **ITB** owns conformance: did the EMR call the API correctly?
- **The upstream** (mock, later UAT) owns realistic behaviour: tokens, records, error responses.

The mock contains no conformance logic, so nothing is lost when UAT replaces it.

### 3.1 New and changed repository pieces

| Path | Purpose |
|---|---|
| `dha-hie-suite/testSuite.xml` | Suite `dha-hie-auth-cr`. Actors: `EMR` (SUT) and `DHA_HIE` (simulated, hidden). |
| `dha-hie-suite/testCases/*.xml` | `AUTH-01`, `CR-01`, `CR-02`, `CR-03` (section 4). |
| `dha-hie-suite/scriptlets/*.xml` | `receiveAndForward`, `checkTokenRequest`, `checkBearer`, `checkPatientQuery`. |
| `dha-hie-suite/resources/fixtures.json` | Test identities each case tells the tester to use, keyed by identifier type, plus the not-found number and expected display names. |
| `dha-hie-suite/README.md` | Setup: domain parameters, pointing the EMR at ITB, Docker networking notes. |
| `mock-dha/mappings/*.json` | WireMock stubs. |
| `mock-dha/__files/**` | Fixture response bodies. |
| `docker-compose.yml` | New `mock-dha` service: `wiremock/wiremock:3.9.1` image, mounts `mock-dha/`, internal URL `http://mock-dha:8080`, host port `8095`. |
| `scripts/package-suite.sh` | Builds a clean suite zip (no top-level folder, no `__MACOSX`). Usable for the existing suites too. |
| `scripts/fake-emr.sh` | Simulated EMR used for verification (section 6). |
| `scripts/check-fixtures.sh` | Checks that every identity in `fixtures.json` resolves in the mock as expected. |

### 3.2 Configuration (ITB domain parameters)

| Parameter | Kind | Initial value | Used for |
|---|---|---|---|
| `dhaBaseUrl` | Simple | `http://mock-dha:8080/uat-middleware/api/v1` | Upstream for forwarding |
| `dhaClientId` | Simple | `itb-test-client` | Expected `client_id` from the EMR; the tester configures it in the EMR |
| `dhaClientSecret` | Secret | generated test value | Expected `client_secret`; hidden in reports |

The URL the EMR must use as its DHA base URL is ITB's receive base, `<gitb-srv>/itbsrv/api/http/<system API key>`. Every test case shows this URL to the tester in its first `instruct` step.

## 4. Test cases

Every case runs with `supportsParallelExecution="false"`, because the receive URL is shared for each system. All cases start with an `interact` step that shows the receive base URL and the instruction for the tester.

### 4.1 Shared scriptlets

- **`receiveAndForward(uriExtension, method, checks)`**
  1. HttpMessagingV2 `receive` on `uriExtension`.
  2. Inside the `result` block:
     - run the given check scriptlet on `$r{request}`;
     - `send` the request unchanged (method, headers including Authorization, query, body) to `$DOMAIN{dhaBaseUrl}` + path;
     - return the upstream status, headers and body to the EMR.
  3. Outputs: the request, the upstream response, and check outcomes.
  4. Check failures do not block the reply, so the EMR always gets the upstream response and never hangs. The failures are reported on the step and fail the test.
  5. `Authorization` and `client_secret` values are hidden from reports with `requestHeadersToHide` and masking.

- **`checkTokenRequest`**
  - The method is POST.
  - `client_id` and `client_secret` are present and equal to `$DOMAIN{dhaClientId}` and `$DOMAIN{dhaClientSecret}`.
  - The content type is form-urlencoded. A JSON body with the right fields is a **warning**, not an error, because the docs contradict each other.

- **`checkBearer`**
  - The `Authorization` header is present and matches `^Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$`.
  - If either `X-Facility-Id` or `X-Facility-Id-Type` is present, both must be present and `X-Facility-Id-Type` must equal `fr-code`.

- **`checkPatientQuery(expectedType, expectedNumber)`**
  - The method is GET.
  - Both query parameters are present.
  - `identification_type` is in the 7-value enum and equals `expectedType` exactly (case and spacing matter). On a mismatch, the failure message names the expected value.
  - `identification_number` equals `expectedNumber`.

### 4.2 Cases

| ID | Tester is told | ITB checks | Pass needs |
|---|---|---|---|
| **AUTH-01** Obtain access token | Configure the EMR with the receive base URL and test credentials, then trigger any DHA action or a token refresh | `receiveAndForward("/tenants/token", POST, checkTokenRequest)`; the upstream returns 200 and the body has `access_token` | No check errors; upstream 200 |
| **CR-01** Lookup by National ID (found) | Search for the client with National ID `<fixtures.nationalId.number>` | `receiveAndForward("/patients", GET, checkBearer + checkPatientQuery)`; upstream 200 | No check errors; upstream 200; tester confirms (yes/no) that the EMR shows `<fixtures.nationalId.displayName>` |
| **CR-02** Other identifier types | For each of ClientRegistry ID, Birth Certificate, Birth Notification, Alien ID, Refugee ID and Mandate Number, search using the fixture number (one `foreach` iteration per type) | The same checks per iteration, with the matching expected type | All iterations pass; tester confirms display each time |
| **CR-03** Client not found | Search for National ID `<fixtures.notFound.number>` | The same request checks; upstream returns 404 | No check errors; tester confirms the EMR shows a "not found" message and does not create or display a client |

**Why CR cases do not wait for a token call:** EMRs cache tokens, so waiting for `/tenants/token` in a CR case would hang. CR cases check the Bearer format locally and leave token validity to the upstream:
- the mock returns 401 for a missing or malformed Bearer;
- UAT will check the signature.

Token acquisition is covered only by AUTH-01.

## 5. Mock DHA (WireMock)

All paths are prefixed with `/uat-middleware/api/v1`.

**`POST /tenants/token`**

| Request | Response |
|---|---|
| Form or JSON body with non-empty `client_id` and `client_secret` | 200 `{"access_token": "<fixture JWT>", "expires_in": 3600, "token_type": "Bearer"}` |
| Either field missing | 400 `{"error": "Bad Request", "message": "client_id and client_secret are required"}` |

The fixture JWT is unsigned and static. Its claims are `tenant_id: "itb-mock-tenant"`, `facility_id: "FID-47-115307-8"` and `facility_id_type: "fr-code"`, making it a facility-scoped token, so the `X-Facility-*` headers are optional.

**`GET /patients`**

Evaluated in order of WireMock stub priority:

| Order | Request | Response |
|---|---|---|
| 1 | No `Authorization`, or no `Bearer <jwt>` | 401 `{code: 401, details: [...], error: "Unauthorized", message, source}` — **assumed shape** |
| 2 | A query parameter is missing, or `identification_type` is not in the enum | 400 with the documented shape |
| 3 | `(identification_type, identification_number)` matches a fixture | 200 with `__files/patients/<fixture>.json` |
| 4 | Anything else | 404 `{code: 404, details: [...], error: "Not Found", message: "Client not found", source}` — **assumed shape** |

**Fixtures**

- One synthetic client per identifier type (7), each with the full documented response shape.
- At least one of them has populated `dependants`.
- The reserved not-found number is `00000000`.
- All data is clearly synthetic, e.g. `first_name: "Test"`, `last_name: "Mock-NationalID"`. No real people and no example IDs copied from the DHA guide.

`dha-hie-suite/resources/fixtures.json` and the mock fixtures must agree. `scripts/check-fixtures.sh` enforces this.

## 6. Verification

There is no real EMR in this repo, so verification uses `scripts/fake-emr.sh` (bash + curl).

1. **Mock smoke** (`fake-emr.sh mock`) calls `mock-dha:8095` directly and checks:
   - token 200 and token 400;
   - patients 200 for each fixture;
   - 404 for the not-found number;
   - 400 for a bad or missing type;
   - 401 without a Bearer.
2. **End-to-end happy path** (`fake-emr.sh itb`)
   - Starts each test case through the ITB REST automation API (already enabled via `AUTOMATION_ENABLED=true`).
   - Makes the correct EMR calls against the receive URL.
   - Answers the tester confirmation prompts through the automation API's interaction input.
   - Checks that every session ends in **SUCCESS**.
3. **End-to-end negative path** (`fake-emr.sh itb --bad`). Makes these deliberately wrong calls and checks that each session ends in **FAILURE** with the expected message:
   - wrong `client_secret`
   - `identification_type=NATIONAL_ID`
   - missing Bearer
   - only one `X-Facility-*` header
4. **`check-fixtures.sh`** passes.

If the automation API can't answer `interact` prompts in the deployed ITB version, steps 2–3 fall back to a manual run in gitb-ui following the README. The README documents this.

## 7. Moving to local UAT (future, configuration only)

1. Set `dhaBaseUrl` to the local UAT base URL. Set `dhaClientId` and `dhaClientSecret` to the UAT-issued credentials.
2. Replace the identities in `resources/fixtures.json` with UAT test identities, then repackage and upload the suite.
3. Confirm the two assumed shapes (401 and 404) against UAT. Correct the mock if they differ.

No test-case XML changes are needed.

## 8. Out of scope

- Health Worker and Facility registries, eClaims and preauths, consent, SHR and the Terminology service. Each is a later spec that reuses these scriptlets and the mock.
- Automated EMR triggering and itb-broker integration.
- Token expiry and refresh behaviour testing.
- Fixing the `$.meta.profile` check in the existing suites.
- CI pipelines.

## 9. Risks & open questions

| Risk / question | Mitigation |
|---|---|
| The EMR may not accept a base URL with a path prefix (`/itbsrv/api/http/<key>`) | Document it in the README. If it's common, add a path-rewriting nginx route later. |
| Token request body format: form or JSON | Accept both; warn on JSON. Confirm with DHA. |
| Identifier type spelling differs between guide and API enum | Enforce the API enum and report the expected value. Confirm with DHA. |
| Undocumented 401 and 404 shapes | Mock uses the registry error shape; confirm against UAT (section 7). |
| `receive` `result` blocks forwarding to upstream may hit handler timeouts | Set `handlerTimeout` and `connectionTimeout` explicitly; verified in end-to-end runs. |
