ALTER TABLE building_lounge_sessions
  ADD COLUMN wifi_fingerprint CHAR(64);

UPDATE building_lounge_sessions
SET active = false, expires_at = now()
WHERE wifi_fingerprint IS NULL;

ALTER TABLE building_lounge_sessions
  ADD CONSTRAINT building_lounge_wifi_fingerprint_check
  CHECK (wifi_fingerprint IS NULL OR wifi_fingerprint ~ '^[0-9a-f]{64}$');

CREATE INDEX building_lounge_sessions_wifi_active_idx
  ON building_lounge_sessions(building_lounge_id, wifi_fingerprint, active, expires_at);
