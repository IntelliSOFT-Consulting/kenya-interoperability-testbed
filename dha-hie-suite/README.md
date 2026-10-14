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
