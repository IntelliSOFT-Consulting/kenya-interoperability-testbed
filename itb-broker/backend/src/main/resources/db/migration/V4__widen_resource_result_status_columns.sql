-- fetch_status / itb_post_status / write_test_status can hold "ERROR: <exception message>"
-- (e.g. DNS resolution failures for portal-supplied SUT endpoints), not just short codes
-- like "200" or "404" — VARCHAR(20) was too narrow and caused save() to fail mid-run.
ALTER TABLE resource_results ALTER COLUMN fetch_status TYPE VARCHAR(255);
ALTER TABLE resource_results ALTER COLUMN itb_post_status TYPE VARCHAR(255);
ALTER TABLE resource_results ALTER COLUMN write_test_status TYPE VARCHAR(255);
