# Certification Portal → Broker: test request contract (proposal)

Not implemented yet. This is the payload the Certification Portal would `POST` to a new broker
endpoint (e.g. `POST /api/portal/test-requests`) to kick off a test run, instead of an admin
manually filling in Screen 2. For review before any code is written.

Files:
- [`portal-test-request.schema.json`](portal-test-request.schema.json) — JSON Schema (draft 2020-12)
- [`portal-test-request-example.json`](portal-test-request-example.json) — a concrete example, validated against the schema

## Design

- **`system`** — organization name, system name, version, and the portal's own system id
  (`certificationPortalSystemId`), matching the fields already on [`SystemConfig`](../backend/src/main/java/ke/go/dha/itb/broker/model/SystemConfig.java).
- **No ITB session id in this payload.** The portal doesn't know it — per the manual-session
  model, an ITB session is only created when an admin manually starts one in gitb-ui. This request
  registers *what* should be tested for a system; it does not itself start a run. Pairing a saved
  request with a real `itbSessionId` and actually kicking off `TestExecutionService` still happens
  the way Screen 2 already works today, just picking from what the portal submitted instead of an
  admin typing it in by hand.
- **`testScenarios`** — an array, so one request can register multiple scenarios for the same
  system in one go (e.g. Patient Summary + eClaims). `scenarioKey` is a fixed enum
  (`PATIENT_SUMMARY`, `ECLAIMS`, `LAB`, `IMMUNIZATION`) — matching the broker's existing YAML
  scenario registry (spec Section 4) — not free text.
- **`testCases`** — the dynamic part, one entry per FHIR resource type to test within that
  scenario. Each carries:
  - `resourceType` — any FHIR resource type, not constrained to the scenario's built-in
    `readResources`/`writeResources` list. This is what makes the payload dynamic: the portal
    decides exactly which resources to exercise per system, rather than the broker always running
    every resource in the YAML scenario.
  - `endpoint` — the **explicit** SUT URL for that resource. Real SUTs frequently don't expose
    FHIR-conventional paths (`{baseUrl}/Patient`) — the example uses `.../patDemo` for Patient,
    `.../obs` for Observation, etc. — so the broker can't derive this by concatenation; the portal
    (or whoever configured the SUT integration) has to supply it directly.
  - `testType` — `READ` routes through Job 1+2 (fetch from `endpoint`, forward to ITB for
    validation). `WRITE` routes through Job 3 (ITB write-verify endpoint) — the broker still never
    posts writes directly to the SUT; `endpoint` on a `WRITE` entry is informational only.

## What happens on receipt (once built)

1. Broker validates the payload against the schema.
2. Broker upserts a `SystemConfig` from `system` + `auth` (matching on
   `certificationPortalSystemId` if present, else creating new).
3. Broker stores the submitted `testScenarios`/`testCases` (a new table, e.g.
   `portal_test_requests`) against that `SystemConfig`, in a `PENDING` state — no ITB session
   attached yet, nothing executes.
4. Separately — an admin still creates the ITB session manually in gitb-ui, same as today. On
   Screen 2 (or a new "Pending Portal Requests" view), the admin picks the pending request instead
   of retyping scenario/testcase details, pastes the `itbSessionId`, and starts the run.
5. From there, execution follows the existing `TestExecutionService` flow per resource, keyed by
   `resourceType` + `endpoint` from the stored request instead of the YAML registry's fixed URL
   pattern. Before running each testcase, broker checks against ITB what's actually
   configured/waiting for that session step (rather than blindly trusting the payload) — exact
   mechanism TBD, likely inspecting the session's current step via ITB's session/report APIs.
   Testcases that don't match anything ITB has configured are marked `SKIPPED` rather than
   aborting the run.

Open questions for review:
- Should mismatches between a testcase and what ITB actually has configured for that session step
  fail the whole run, or just skip the unmatched testcase and proceed with the rest?
- Does the admin manually pick *which* pending portal request to pair with a new ITB session
  (Screen 2 gets a dropdown), or is there a separate acceptance/review step first?
