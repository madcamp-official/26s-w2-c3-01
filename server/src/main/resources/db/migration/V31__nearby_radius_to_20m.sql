ALTER TABLE user_privacy_settings
  DROP CONSTRAINT IF EXISTS user_privacy_radius_check;

UPDATE user_privacy_settings
SET discovery_radius_meters = 20
WHERE discovery_radius_meters <> 20;

ALTER TABLE user_privacy_settings
  ALTER COLUMN discovery_radius_meters SET DEFAULT 20,
  ADD CONSTRAINT user_privacy_radius_check CHECK (discovery_radius_meters = 20);

UPDATE direct_proximity_measurements
SET proximity = CASE
  WHEN proximity IN ('WITHIN_5M', 'WITHIN_10M', 'VERY_CLOSE', 'CLOSE') THEN 'WITHIN_10M'
  ELSE 'WITHIN_20M'
END;
