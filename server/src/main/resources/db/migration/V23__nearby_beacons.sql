CREATE TABLE nearby_beacons (
  beacon_id VARCHAR(40) PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_session_id VARCHAR(128) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX nearby_beacons_user_expiry_idx
  ON nearby_beacons(user_id, expires_at DESC);
CREATE INDEX nearby_beacons_expiry_idx ON nearby_beacons(expires_at);
