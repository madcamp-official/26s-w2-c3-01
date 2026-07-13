ALTER TABLE user_privacy_settings
  DROP CONSTRAINT IF EXISTS user_privacy_radius_check;

UPDATE user_privacy_settings
SET discovery_radius_meters = 15
WHERE discovery_radius_meters <> 15;

ALTER TABLE user_privacy_settings
  ALTER COLUMN discovery_radius_meters SET DEFAULT 15,
  ADD CONSTRAINT user_privacy_radius_check CHECK (discovery_radius_meters = 15);
