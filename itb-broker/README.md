# ITB Automation Broker

Automates FHIR conformance testing between a System Under Test (SUT) and the EC Interoperability Test Bed (ITB). Replaces the manual Postman workflow: the admin still creates the test session in gitb-ui, then pastes the session ID into the broker UI, which fetches FHIR resources from the SUT, forwards them to ITB for validation, optionally runs write-and-verify tests via ITB, downloads the conformance certificate, and uploads it to the Certification Portal.

## Layout

- `backend/` — Java 17 / Spring Boot 3 API on port 8090 (PostgreSQL 15 + Flyway)
- `frontend/` — React 18 + TypeScript + Tailwind control panel (served by nginx on port 3001)
- `certificates/` — downloaded certificate PDFs (bind-mounted into broker-api)

## Run

Services are wired into the repo-root `docker-compose.yml` (`broker-db`, `broker-api`, `broker-frontend`). Copy the variables from `.env.example` into the top-level `.env`, then:

```
docker compose up -d broker-db broker-api broker-frontend
```

UI: http://localhost:3001 — API: http://localhost:8090 (Swagger at /swagger-ui.html)

## Development

```
mvn -f backend/pom.xml test
cd frontend && npm install && npm run dev   # proxies /api to localhost:8090
```

## Critical constraints (spec Section 11)

1. The broker never creates ITB sessions — session creation is manual in gitb-ui.
2. ITB endpoint pattern is fixed: `{itbBaseUrl}/itbsrv/api/http/{sessionId}/{resourceType}` (append `/write` for write tests), resource type **lowercased** in the URL (the FHIR JSON body's `resourceType` field stays correctly cased). Confirmed live against a real ICL session: a POST to `/itbsrv/api/http/{sessionId}/patient` returned 200 and advanced the session to its next step. Note: gitb-srv's own "waiting to receive" log line is misleading — it's built from `CALLBACK_ROOT_URL` and omits the `/itbsrv` context path in this deployment, so don't trust that log line's URL literally.
3. Write tests go through ITB, never directly to the SUT.
4. The automation endpoint is only live while gitb-ui has the session paused at the matching "receive" test step (it's a per-step listener with a timeout, not a standing route) — the admin must trigger/be at that step in gitb-ui when the broker posts, or the POST 404s even with a correct URL and a real session ID.
