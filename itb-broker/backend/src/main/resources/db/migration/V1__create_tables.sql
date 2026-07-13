CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE system_configs (
  id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  system_name                     VARCHAR(255) NOT NULL,
  sut_base_url                    TEXT NOT NULL,
  auth_type                       VARCHAR(50) NOT NULL DEFAULT 'BEARER',
  auth_token                      TEXT,
  certification_portal_system_id  VARCHAR(255),
  created_at                      TIMESTAMP DEFAULT now(),
  updated_at                      TIMESTAMP DEFAULT now()
);

CREATE TABLE test_sessions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  system_config_id    UUID REFERENCES system_configs(id),
  itb_session_id      VARCHAR(255) NOT NULL,
  itb_base_url        TEXT NOT NULL,
  test_scenario       VARCHAR(100) NOT NULL,
  patient_id          VARCHAR(100),
  write_test_enabled  BOOLEAN DEFAULT false,
  status              VARCHAR(50) DEFAULT 'CONFIGURED',
  certificate_path    TEXT,
  started_at          TIMESTAMP,
  completed_at        TIMESTAMP
);

CREATE TABLE resource_results (
  id                    BIGSERIAL PRIMARY KEY,
  test_session_id       UUID REFERENCES test_sessions(id),
  resource_type         VARCHAR(100) NOT NULL,
  -- Job 1+2: Read and validate
  fetch_status          VARCHAR(20),
  fetched_payload       TEXT,
  itb_post_status       VARCHAR(20),
  itb_response          TEXT,
  -- Job 3: Write and verify via ITB
  write_test_status     VARCHAR(20),
  write_test_response   TEXT,
  write_verify_passed   BOOLEAN,
  write_verify_diff     TEXT,
  tested_at             TIMESTAMP DEFAULT now()
);
