CREATE TABLE IF NOT EXISTS taste_track_catalog (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  canonical_key VARCHAR(400) NOT NULL UNIQUE,
  provider VARCHAR(32),
  provider_track_id VARCHAR(160),
  title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  album_name VARCHAR(160),
  duration_ms BIGINT,
  genre_tags VARCHAR(240) NOT NULL DEFAULT '',
  mood_tags VARCHAR(240) NOT NULL DEFAULT '',
  embedding REAL[],
  embedding_model_version VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 86400000)
);

CREATE UNIQUE INDEX IF NOT EXISTS taste_track_catalog_provider_idx
  ON taste_track_catalog(provider, provider_track_id)
  WHERE provider IS NOT NULL AND provider_track_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS music_listen_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_event_id UUID NOT NULL,
  track_id UUID REFERENCES taste_track_catalog(id) ON DELETE SET NULL,
  canonical_key VARCHAR(400) NOT NULL,
  title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  album_name VARCHAR(160),
  provider VARCHAR(32),
  provider_track_id VARCHAR(160),
  source_type VARCHAR(32) NOT NULL,
  source_package VARCHAR(160),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NOT NULL,
  played_ms BIGINT NOT NULL,
  duration_ms BIGINT,
  completion_ratio REAL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_event_id),
  CHECK (ended_at >= started_at),
  CHECK (played_ms BETWEEN 0 AND 86400000),
  CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 86400000),
  CHECK (completion_ratio IS NULL OR completion_ratio BETWEEN 0 AND 1)
);

CREATE INDEX IF NOT EXISTS music_listen_events_user_time_idx
  ON music_listen_events(user_id, ended_at DESC);
CREATE INDEX IF NOT EXISTS music_listen_events_track_time_idx
  ON music_listen_events(canonical_key, ended_at DESC);

CREATE TABLE IF NOT EXISTS taste_model_versions (
  model_version VARCHAR(80) PRIMARY KEY,
  algorithm_version VARCHAR(80) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SHADOW',
  artifact_uri TEXT,
  calibration JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at TIMESTAMPTZ,
  CHECK (status IN ('SHADOW', 'ACTIVE', 'RETIRED'))
);

INSERT INTO taste_model_versions(model_version, algorithm_version, status, calibration, activated_at)
VALUES (
  'taste-bootstrap-v2',
  'HYBRID_SIAMESE_V2_BOOTSTRAP',
  'ACTIVE',
  '{"center":50,"dimensions":128,"minimumEvidence":3}'::jsonb,
  now()
)
ON CONFLICT(model_version) DO NOTHING;

CREATE TABLE IF NOT EXISTS taste_user_embeddings (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  model_version VARCHAR(80) NOT NULL REFERENCES taste_model_versions(model_version),
  algorithm_version VARCHAR(80) NOT NULL,
  embedding REAL[] NOT NULL,
  evidence_count INTEGER NOT NULL,
  confidence VARCHAR(16) NOT NULL,
  source_mask INTEGER NOT NULL DEFAULT 0,
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (evidence_count >= 0),
  CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
  PRIMARY KEY(user_id, model_version)
);

CREATE INDEX IF NOT EXISTS taste_user_embeddings_version_idx
  ON taste_user_embeddings(model_version, calculated_at DESC);

CREATE TABLE IF NOT EXISTS taste_embedding_jobs (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  reason VARCHAR(40) NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  claimed_at TIMESTAMPTZ,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error VARCHAR(500),
  CHECK (attempts >= 0)
);

INSERT INTO taste_embedding_jobs(user_id, reason)
SELECT id, 'INITIAL_BACKFILL' FROM users
ON CONFLICT(user_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS taste_match_exposures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  viewer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  context VARCHAR(24) NOT NULL,
  score SMALLINT,
  confidence VARCHAR(16) NOT NULL,
  algorithm_version VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (viewer_user_id <> target_user_id),
  CHECK (context IN ('NEARBY', 'PROFILE', 'LOUNGE')),
  CHECK (score IS NULL OR score BETWEEN 0 AND 100),
  CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX IF NOT EXISTS taste_match_exposures_viewer_time_idx
  ON taste_match_exposures(viewer_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS taste_match_feedback (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_feedback_id UUID NOT NULL,
  exposure_id UUID REFERENCES taste_match_exposures(id) ON DELETE SET NULL,
  actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  action VARCHAR(32) NOT NULL,
  strength REAL NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(actor_user_id, client_feedback_id),
  CHECK (actor_user_id <> target_user_id),
  CHECK (action IN ('PROFILE_OPEN', 'PREVIEW_PLAY', 'MUSIC_APP_OPEN', 'SAME_TASTE', 'FOLLOW', 'MUTUAL_FOLLOW', 'CHAT', 'BLOCK')),
  CHECK (strength BETWEEN -1 AND 1)
);

CREATE INDEX IF NOT EXISTS taste_match_feedback_pair_time_idx
  ON taste_match_feedback(actor_user_id, target_user_id, created_at DESC);
