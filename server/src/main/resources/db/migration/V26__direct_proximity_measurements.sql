-- Store short-lived direct device proximity measurements separately from location data.
CREATE TABLE direct_proximity_measurements (
  viewer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  proximity VARCHAR(24) NOT NULL,
  confidence VARCHAR(16) NOT NULL,
  method VARCHAR(24) NOT NULL,
  sequence BIGINT NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (viewer_user_id, target_user_id)
);

CREATE INDEX direct_proximity_expiry_idx ON direct_proximity_measurements(expires_at);
