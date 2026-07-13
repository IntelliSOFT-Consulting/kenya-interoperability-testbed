ALTER TABLE resource_results
  ADD COLUMN test_type    VARCHAR(20),
  ADD COLUMN sut_endpoint TEXT,
  ADD COLUMN itb_endpoint TEXT;
