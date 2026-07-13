ALTER TABLE offline_credentials
  DROP CONSTRAINT IF EXISTS offline_credentials_public_subject_key;

CREATE INDEX IF NOT EXISTS offline_credentials_public_subject_idx
  ON offline_credentials(public_subject);
