ALTER TABLE system_configs ALTER COLUMN sut_base_url DROP NOT NULL;

CREATE TABLE portal_test_requests (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  system_config_id  UUID REFERENCES system_configs(id),
  request_id        VARCHAR(255),
  submitted_at      TIMESTAMP,
  patient_id        VARCHAR(100),
  status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  created_at        TIMESTAMP DEFAULT now()
);

CREATE TABLE portal_test_scenarios (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  portal_test_request_id  UUID REFERENCES portal_test_requests(id),
  scenario_key            VARCHAR(100) NOT NULL,
  test_session_id         UUID REFERENCES test_sessions(id)
);

CREATE TABLE portal_test_cases (
  id                        BIGSERIAL PRIMARY KEY,
  portal_test_scenario_id  UUID REFERENCES portal_test_scenarios(id),
  resource_type             VARCHAR(100) NOT NULL,
  endpoint                  TEXT NOT NULL,
  test_type                 VARCHAR(20) NOT NULL
);
