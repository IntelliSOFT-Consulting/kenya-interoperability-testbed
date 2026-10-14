# DHA HIE APIs — Authentication & Client Registry Conformance Suite

**Date:** 2026-10-14
**Status:** Approved; implemented (see §11 for decisions made during implementation)
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
| Externally hosted EMRs | Supported through a public **lab gateway** (nginx) that maps a clean per-vendor base URL to ITB; credentials are set **per system** (section 7). |
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

All hosts are written using their **network aliases** (section 3.3). These are the names containers use to reach each other on the compose network.

```text
                         docker network (compose, default)
                         ┌───────────────────────────────────────────────────────────────────────────┐
EMR (SUT) ──HTTP(S)──►   │ lab-gateway (nginx)                                                       │
 local or vendor-hosted  │   <vendor> host → /itbsrv/api/http/<apiKey>/…                             │
 HIE base URL =          │        │                                                                  │
  https://<vendor>.      │        ▼                                                                  │
    <LAB_GATEWAY_DOMAIN> │ lab-service :8080   HttpMessagingV2 receive                               │
  (or raw <gateway>/     │   validates request ──forward──► mock-hie :8080 (WireMock)                │
   itbsrv/api/http/<key>)│   relays upstream      upstream = $DOMAIN{hieBaseUrl}                     │
                         │   response to EMR      now:   http://mock-hie:8080/uat-middleware/api/v1  │
                         │        ▲               later: local UAT base URL                          │
                         │        │ lab-ui starts and drives sessions on lab-service                 │
                         │        │                                                                  │
Tester (browser) ─────►  │ lab-ui-proxy (nginx) ──► lab-ui :9000  reports, interact prompts          │
                         │                          uses lab-db (MySQL), lab-cache (Redis)           │
                         │                                                                           │
                         │ lab-fhir (HAPI FHIR): existing FHIR suites only                           │
                         └───────────────────────────────────────────────────────────────────────────┘
```

The EMR always initiates the calls; ITB never calls the EMR. An EMR anywhere, including one hosted by a vendor, therefore only needs outbound HTTPS to the gateway. The vendor's network needs no inbound access.

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
| `mock-hie/mappings/*.json` | WireMock stubs. |
| `mock-hie/__files/**` | Fixture response bodies. |
| `docker-compose.yml` | New `mock-hie` service: `wiremock/wiremock:3.9.1` image, mounts `mock-hie/`, internal URL `http://mock-hie:8080`, host port `8095`. New `lab-gateway` service: `nginx:1.27-alpine` image, host port `9010` (HTTP, local). |
| `docker-compose.public.yml` | Override for internet-facing deployment (section 7): TLS on 443 for the gateway and lab UI, `CALLBACK_ROOT_URL` set to the public host, and `ports: !reset []` on the `gitb-srv` (lab-service) and `mock-hie` services so they are not published on the host. Requires Docker Compose ≥ 2.24. |
| `nginx/lab-gateway.conf` | Gateway config: vendor host → API key mapping, the raw receive path, rate limiting, and 404 for everything else (section 7.2). |
| `nginx/lab-gateway-receive.inc`, `nginx/lab-gateway-vendor.inc` | Shared locations for the raw receive path and vendor-host routing, included by the HTTP and TLS servers. |
| `nginx/lab-gateway-tls.conf.template` | TLS server blocks (vendor wildcard host and the lab UI host), rendered by the nginx image's envsubst; used only by the public override. |
| `nginx/lab-vendors.map.example` | Committed example of the vendor host → API key map. The real map is `nginx/lab/lab-vendors.map` (gitignored directory, mounted as a directory so edits are seen on reload). |
| `nginx/lab-allowlist.conf.example` | Optional IP allowlist example. The real file is `nginx/lab/lab-allowlist.conf`; comments only means allow all. |
| `scripts/init-lab-gateway.sh` | Creates `nginx/lab/` from the examples without overwriting. |
| `docker-compose.yml` (existing services) | Add network aliases to the existing services, keeping their service names (section 3.3). |
| `.gitignore` | Add `nginx/lab-vendors.map`, `nginx/lab-allowlist.conf`, `nginx/certs/`. |
| `scripts/package-suite.sh` | Builds a clean suite zip (no top-level folder, no `__MACOSX`). Usable for the existing suites too. |
| `scripts/fake-emr.sh` | Simulated EMR used for verification (section 6). |
| `mock-hie/generate_fixtures.py` | Generates the mock's Client Registry stubs and bodies from `dha-hie-suite/resources/fixtures.json` (the single source of identities). |
| `scripts/check-fixtures.sh` | Fails when the generated mock files don't match `fixtures.json`; `--write` regenerates them. |
| `scripts/validate-suite.sh` | Validates the suite XML against the GITB TDL XSD. |
| `scripts/check-aliases.sh`, `scripts/check-public-overlay.sh` | Check the network aliases and the merged public overlay. |

### 3.2 Configuration

**Domain parameters** (shared by all systems):

| Parameter | Kind | Initial value | Used for |
|---|---|---|---|
| `hieBaseUrl` | Simple | `http://mock-hie:8080/uat-middleware/api/v1` | Upstream for forwarding |
| `labServiceBaseUrl` | Simple | `http://localhost:9010/itbsrv/api/http` | Raw receive base through lab-gateway, shown to testers (no TDL variable exposes it) |

**System properties** (one set per EMR system, defined in the community's custom system properties):

| Property | Kind | Required | Editable by vendor | In tests | Used for |
|---|---|---|---|---|---|
| `hieClientId` | Simple | Yes | No | Yes | Expected `client_id` from this EMR |
| `hieClientSecret` | Secret | Yes | No | Yes | Expected `client_secret`; hidden in reports |
| `labGatewayBaseUrl` | Simple | No | No | Yes | This vendor's clean base URL, e.g. `https://acme-emr.lab-itb.example.org` |

- The admin issues the credentials for each vendor system and sends them to the vendor out-of-band. This mirrors DHA, which issues credentials per app.
- Per-system credentials stop vendors sharing one secret and allow revoking a single vendor.

**The URL shown to the tester.** Every test case's first `instruct` step shows the HIE base URL the EMR must use:

| `labGatewayBaseUrl` | URL shown |
|---|---|
| Set | `$SYSTEM{labGatewayBaseUrl}` |
| Not set | The raw receive base, `$DOMAIN{labServiceBaseUrl}/$SYSTEM{apiKey}` |

### 3.3 Service aliases

The existing services keep their compose **service names**. This matters because the GITB images find each other by their default hostnames: gitb-ui expects `gitb-srv`, `gitb-mysql` and `gitb-redis`.

The `lab-*` names are added as Docker **network aliases** on the default network. Every container on the network can resolve both names.

| Service name (compose key) | Network alias | Role |
|---|---|---|
| `gitb-srv` | `lab-service` | Test engine; receives EMR calls |
| `gitb-ui` | `lab-ui` | Web UI and automation API |
| `gitb-ui-proxy` | `lab-ui-proxy` | nginx in front of the UI |
| `gitb-mysql` | `lab-db` | UI database |
| `gitb-redis` | `lab-cache` | UI cache |
| `hapi-fhir-kenyaitb` | `lab-fhir` | FHIR validator and store for the existing suites |
| `mock-hie` | (its service name) | Mock HIE upstream |
| `lab-gateway` | (its service name) | Gateway for EMR traffic |

Example:

```yaml
gitb-srv:
  networks:
    default:
      aliases:
        - lab-service
```

**Rules:**

- **New config uses aliases.** This covers the nginx `proxy_pass` targets and the domain parameter values (`hieBaseUrl`).
- **Existing config is left unchanged.** The current FHIR suites' hardcoded `http://hapi-fhir-kenyaitb:8080` URLs keep working.
- **Aliases only affect DNS inside the network.** `docker compose exec/logs` still take the service name, and `docker ps` still shows the existing `container_name` values (`itb-srv`, `itb-ui`, …), which stay as they are.

## 4. Test cases

Every case runs with `supportsParallelExecution="false"`, because the receive URL is shared for each system. All cases start with an `interact` step that shows the receive base URL and the instruction for the tester.

### 4.1 Shared scriptlets

Checks run **after** the reply: the receive's `result` block only forwards upstream and relays the response, then the caller checks `$emrCall{request}` and `$emrCall{response}`. A failing check therefore never blocks the EMR. Helper scriptlets: `normaliseRequest` (canonical header names, accepting lower-case, plus the query string from the URI or the `X-Lab-Original-Uri` header), `formField` (decodes one field of form-encoded text, `+` or `%20`) and `emrBaseUrl` (the URL shown to testers).

- **`receiveAndForward(uriExtension, method, checks)`**
  1. HttpMessagingV2 `receive` on `uriExtension`.
  2. Inside the `result` block:
     - run the given check scriptlet on `$r{request}`;
     - `send` the request unchanged (method, headers including Authorization, query, body) to `$DOMAIN{hieBaseUrl}` + path;
     - return the upstream status, headers and body to the EMR.
  3. Outputs: the request, the upstream response, and check outcomes.
  4. Check failures do not block the reply, so the EMR always gets the upstream response and never hangs. The failures are reported on the step and fail the test.
  5. `Authorization` and `client_secret` values are hidden from reports with `requestHeadersToHide` and masking.

- **`checkTokenRequest`**
  - The method is POST.
  - `client_id` and `client_secret` are present and equal to `$SYSTEM{hieClientId}` and `$SYSTEM{hieClientSecret}`.
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

**Token requests during CR cases:** EMRs usually cache tokens, but an expired cache or a per-call token means a `POST /tenants/token` can arrive before the lookup. CR cases therefore wait with `receiveLookup`: a receive without `uriExtension` that accepts any call for the system. Token requests are forwarded and answered but not graded, and the wait continues. `GET /patients` ends the wait and is checked. Any other call, or more than 5 token requests, fails the test with a message naming the call. CR cases never *require* a token call. They check the Bearer format locally and leave token validity to the upstream:
- the mock returns 401 for a missing or malformed Bearer;
- UAT will check the signature.

Token acquisition is covered only by AUTH-01.

## 5. Mock HIE (WireMock)

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

1. **Mock smoke** (`fake-emr.sh mock`) calls `mock-hie:8095` directly and checks:
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
5. **Gateway** (`fake-emr.sh gateway`) uses `curl --resolve test-vendor.lab.localhost:9010:127.0.0.1` against a test entry in `nginx/lab-vendors.map`. It checks four cases:

   | Request | Expected result |
   |---|---|
   | The happy path of step 2, sent through the clean base URL | Every session ends in **SUCCESS** |
   | An unknown host | 404 |
   | `/itbsrv/api/rest/...` through the gateway | 404, because the automation API is not exposed |
   | More than the rate limit | 429 |

ITB 1.30.1 has no API for answering `interact` prompts. Every test case declares a `skipInteractions` input (default `false`) that skips the confirmation prompts; `fake-emr.sh` sets it to `true` through `inputMapping` when starting sessions. Instruction pop-ups are non-blocking.

Additional runs (inputs real EMRs send that the cases above don't): token form with `grant_type`/`scope`, JSON token body (warning only), query with `+` for spaces, reordered and extra parameters, lower-case `authorization`, and the upstream (`mock-hie`) stopped, which must end in FAILURE without hanging.

## 7. Deployment for externally hosted (vendor) EMRs

### 7.1 Exposure model

The internet only ever reaches two things: the **lab gateway** and the **lab UI** (`lab-ui`, for vendor testers). Both are served over TLS by `lab-gateway` in the public override.

| Component | Local (`docker-compose.yml`) | Public (`+ docker-compose.public.yml`) |
|---|---|---|
| `lab-gateway` | HTTP on `:9010` | HTTPS on `:443` for `*.<LAB_GATEWAY_DOMAIN>` and `<ITB_UI_HOST>` |
| `lab-ui-proxy` (`gitb-ui-proxy`) | HTTP on `:9000` | Not published. Reached via `lab-gateway`, which serves `https://<ITB_UI_HOST>` and proxies to it, including WebSockets with a 3600s timeout. |
| `lab-service` (`gitb-srv`) | `:8080` | Not published. Internal only: receive paths go through the gateway, and the automation API is not reachable from outside. |
| `mock-hie` | `:8095` | Not published. Internal only. |
| `CALLBACK_ROOT_URL` | `http://localhost:8080` | `https://<ITB_UI_HOST>` (or the gateway host), so the URLs ITB shows are reachable by vendors |

- **TLS certificates:**
  - A wildcard certificate for `*.<LAB_GATEWAY_DOMAIN>` and a certificate for `<ITB_UI_HOST>`, mounted from `nginx/certs/` (gitignored).
  - Getting them is an operations prerequisite: a Let's Encrypt wildcard needs a DNS-01 challenge. Automating it is out of scope; the README documents the expected file names.
- **DNS:** a wildcard record `*.<LAB_GATEWAY_DOMAIN>` pointing to the host.
- **Hostnames:** `LAB_GATEWAY_DOMAIN` and `ITB_UI_HOST` are set in `.env`.

### 7.2 Clean base URL mapping (`nginx/lab-gateway.conf`)

**Goal:** each vendor EMR sees a bare host that looks like DHA's, with no path prefix and no API key in its configuration.

```text
https://acme-emr.lab-itb.example.org/tenants/token
        └─ Host header ─┘            └─ path + query kept as-is ─┘
   ──► http://lab-service:8080/itbsrv/api/http/<acme API key>/tenants/token
```

- **`map $host $vendor_api_key { default ""; include /etc/nginx/lab-vendors.map; }`**
  - Each line in the map file is `<full hostname> <ITB system API key>;`. For example, `acme-emr.lab-itb.example.org 7F3A...;`
  - Local testing uses `*.lab.localhost` names.
- **Vendor server block** (`server_name *.<LAB_GATEWAY_DOMAIN> *.lab.localhost`):
  - If `$vendor_api_key` is empty, it returns 404 `{"error":"Not Found","message":"Unknown vendor host"}`.
  - Otherwise, `location / { proxy_pass http://lab-service:8080/itbsrv/api/http/$vendor_api_key$request_uri; }`.
  - `Host`, `X-Forwarded-For` and `X-Forwarded-Proto` are set on the proxied request.
- **Headers added:** every proxied request carries `X-Lab-Original-Uri: $request_uri`, because the ITB docs don't say whether a receive step's `{request}{uri}` keeps the query string; every proxied response carries `X-Lab-Upstream: lab-service`. All EMR traffic, including local runs, therefore goes through the gateway.
- **Raw receive path:** the default server block on the gateway also accepts `/itbsrv/api/http/` and proxies it unchanged. This serves EMRs configured with the raw URL, and admins who have not added a map entry yet.
- **Everything else is denied.** Any other path on the gateway, including `/itbsrv/api/rest/` (the automation API) and lab-service's other endpoints, returns 404.
- **Rate limiting:** `limit_req_zone $binary_remote_addr zone=lab:10m rate=10r/s` with `burst=20 nodelay`. Excess requests get 429.
- **Optional IP allowlist:** `include /etc/nginx/lab-allowlist.conf;` holding `allow <vendor egress IP>; ... deny all;`. An empty file means no restriction.
- **Body and timeouts:**
  - `client_max_body_size 5m`.
  - `proxy_read_timeout 120s`, which covers ITB checking the request and forwarding it upstream.
- **No secrets in logs:** the access log uses a format without the `Authorization` header and without request bodies.

**Onboarding a vendor** (README checklist):

1. Create the organisation and system in the lab UI, and note the system API key.
2. Set the system properties `hieClientId`, `hieClientSecret` and `labGatewayBaseUrl = https://<vendor>.<LAB_GATEWAY_DOMAIN>`.
3. Add `<vendor>.<LAB_GATEWAY_DOMAIN> <API key>;` to `nginx/lab/lab-vendors.map`. Optionally add the vendor's egress IP to the allowlist.
4. Run `docker compose exec lab-gateway nginx -s reload`.
5. Send the vendor the base URL and credentials out-of-band.

**Revoking a vendor:** remove the map line and reload nginx. If the key has leaked, also regenerate the system API key in the lab UI.

### 7.3 Running a session with a vendor

- The vendor tester logs into the lab UI under their own organisation, starts the test case, and performs the EMR actions.
- Alternatively, an admin runs the session while the vendor drives the EMR on a call.
- `receive` steps wait for the EMR's call. The README tells testers to have the EMR ready before starting a case.
- Fixtures are synthetic only. This is mandatory, because the endpoint is internet-facing.

### 7.4 Fallback when ITB isn't publicly hosted yet

For a one-off vendor session from a non-public machine, expose **only** `lab-gateway:9010` through a tunnel such as `cloudflared` or `ngrok`, and set `CALLBACK_ROOT_URL` accordingly.

With a single tunnel hostname the vendor uses the raw receive path, because wildcard vendor hosts aren't available on most tunnels.

This is documented only and is not for ongoing use.

## 8. Moving to local UAT (future, configuration only)

1. Set `hieBaseUrl` to the local UAT base URL. Set each system's `hieClientId` and `hieClientSecret` to the UAT-issued credentials.
2. Replace the identities in `resources/fixtures.json` with UAT test identities, then repackage and upload the suite.
3. Confirm the two assumed shapes (401 and 404) against UAT. Correct the mock if they differ.

No test-case XML changes are needed.

## 9. Out of scope

- Health Worker and Facility registries, eClaims and preauths, consent, SHR and the Terminology service. Each is a later spec that reuses these scriptlets and the mock.
- Automated EMR triggering and itb-broker integration.
- Automated TLS certificate issuance and renewal.
- Token expiry and refresh behaviour testing.
- Fixing the `$.meta.profile` check in the existing suites.
- CI pipelines.

## 10. Risks & open questions

| Risk / question | Mitigation |
|---|---|
| The EMR may not accept a base URL with a path prefix | Solved by the clean base URL mapping (section 7.2). The raw path is kept as an alternative. |
| `proxy_pass` to a variable URL needs a `resolver` for `lab-service` | Use Docker's embedded DNS, `resolver 127.0.0.11 valid=30s`. Verified by the gateway test (section 6, step 5). |
| ITB-generated report links and `CALLBACK_ROOT_URL` behind a TLS proxy | Set `CALLBACK_ROOT_URL` to the public host in the override. Verify that a vendor tester sees correct URLs in the UI. |
| The internet-facing endpoint gets abused | Map-only routing, rate limit, optional IP allowlist, synthetic data only, and the automation API not exposed. |
| The API key leaks through the raw path | Prefer clean hosts for vendors. Revoke by regenerating the system API key and updating the map. |
| Token request body format: form or JSON | Accept both; warn on JSON. Confirm with DHA. |
| Identifier type spelling differs between guide and API enum | Enforce the API enum and report the expected value. Confirm with DHA. |
| Undocumented 401 and 404 shapes | Mock uses the registry error shape; confirm against UAT (section 8). |
| `receive` `result` blocks forwarding to upstream may hit handler timeouts | `result timeout` 60 s and receive timeout 15 min set explicitly; verified in end-to-end runs. |
| The query string may not be kept on `{request}{uri}` (undocumented) | lab-gateway adds `X-Lab-Original-Uri`; `normaliseRequest` falls back to it. |
| An upstream failure shows only a generic step error in the UI | The cause is in the session log (`Forward to the HIE upstream … [Connect]`); the README points testers there. |

## 11. Implementation decisions (2026-10-14)

- **ITB layout:** a dedicated domain `Kenya HIE`, a specification `DHA HIE APIs`, and a community `Kenya HIE community` bound to that domain (the only existing domain was ITB's `Sample domain`). The community allows *Manage test sessions via REST API*, which the automation scripts need.
- **Interaction prompts:** skipped through the `skipInteractions` input in automated runs (§6).
- **Receive base URL:** domain parameter `labServiceBaseUrl` (§3.2).
- **Checks after reply** and helper scriptlets (§4.1); forwarding is shared in `forwardUpstream`.
- **Token requests in CR cases** are answered, not graded (`receiveLookup`, §4.2), so EMRs with expired or per-call tokens don't hang a lookup test.
- **Generated mock fixtures** from `fixtures.json` (§3.1).
- **Gateway files** under `nginx/lab/`, shared `.inc` snippets, TLS template (§3.1, §7.2).
- `AUTOMATION_ENABLED` in `docker-compose.yml` is unchanged: the running instance already serves `/api/rest`.
