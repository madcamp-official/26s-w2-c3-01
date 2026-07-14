CREATE TABLE wifi_lounge_candidates (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  wifi_fingerprint CHAR(64) NOT NULL,
  wifi_name VARCHAR(80) NOT NULL,
  point geometry(Point, 4326) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT wifi_lounge_candidates_fingerprint_check
    CHECK (wifi_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX wifi_lounge_candidates_wifi_active_idx
  ON wifi_lounge_candidates(wifi_fingerprint, expires_at);
CREATE INDEX wifi_lounge_candidates_point_idx
  ON wifi_lounge_candidates USING GIST(point);

-- Old Wi-Fi lounges used a fixed 300 m radius and were created by a single user.
-- They must not remain visible after switching to the two-person dynamic rule.
UPDATE building_lounges lounge
SET active = false
FROM lounge_buildings building
WHERE lounge.building_id = building.id AND building.category = 'WIFI';
