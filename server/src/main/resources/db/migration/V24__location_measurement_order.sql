ALTER TABLE current_locations ADD COLUMN sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE current_locations ADD COLUMN observed_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE current_locations ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN';
CREATE INDEX current_locations_sequence_idx ON current_locations (session_id, sequence DESC);
