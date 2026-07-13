ALTER TABLE users
  ADD COLUMN IF NOT EXISTS profile_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS profile_signature_tracks (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rank SMALLINT NOT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  provider_track_id VARCHAR(160),
  title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  album_name VARCHAR(160),
  artwork_url TEXT,
  genre_tags VARCHAR(240) NOT NULL DEFAULT '',
  mood_tags VARCHAR(240) NOT NULL DEFAULT '',
  selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, rank),
  CHECK (rank BETWEEN 1 AND 3)
);

CREATE INDEX IF NOT EXISTS profile_signature_tracks_provider_idx
  ON profile_signature_tracks(provider, provider_track_id)
  WHERE provider_track_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS profile_favorite_artists (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rank SMALLINT NOT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  provider_artist_id VARCHAR(160),
  artist_name VARCHAR(160) NOT NULL,
  image_url TEXT,
  genre_tags VARCHAR(240) NOT NULL DEFAULT '',
  selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, rank),
  CHECK (rank BETWEEN 1 AND 3)
);

CREATE INDEX IF NOT EXISTS profile_favorite_artists_provider_idx
  ON profile_favorite_artists(provider, provider_artist_id)
  WHERE provider_artist_id IS NOT NULL;

ALTER TABLE user_privacy_settings
  ADD COLUMN IF NOT EXISTS current_music_visibility VARCHAR(24) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS listening_insights_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS listening_insights_visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN IF NOT EXISTS exchange_insights_visibility VARCHAR(24) NOT NULL DEFAULT 'EXCHANGED',
  ADD COLUMN IF NOT EXISTS bubble_presence_visibility VARCHAR(24) NOT NULL DEFAULT 'PARTICIPANTS_ONLY';

UPDATE user_privacy_settings
SET current_music_visibility = CASE music_visibility
  WHEN 'MUTUALS' THEN 'MUTUALS'
  WHEN 'HIDDEN' THEN 'PRIVATE'
  ELSE 'EVERYONE'
END;

ALTER TABLE user_privacy_settings
  ADD CONSTRAINT user_privacy_current_music_visibility_check
    CHECK (current_music_visibility IN ('EVERYONE', 'MUTUALS', 'PRIVATE')),
  ADD CONSTRAINT user_privacy_listening_insights_visibility_check
    CHECK (listening_insights_visibility IN ('EVERYONE', 'MUTUALS', 'PRIVATE')),
  ADD CONSTRAINT user_privacy_exchange_insights_visibility_check
    CHECK (exchange_insights_visibility IN ('EVERYONE', 'MUTUALS', 'EXCHANGED', 'PRIVATE')),
  ADD CONSTRAINT user_privacy_bubble_presence_visibility_check
    CHECK (bubble_presence_visibility IN ('PARTICIPANTS_ONLY', 'MUTUALS', 'PRIVATE'));

ALTER TABLE music_statuses
  ADD COLUMN IF NOT EXISTS album_name VARCHAR(160),
  ADD COLUMN IF NOT EXISTS normalized_track_id VARCHAR(200),
  ADD COLUMN IF NOT EXISTS duration_ms BIGINT,
  ADD COLUMN IF NOT EXISTS position_ms BIGINT,
  ADD COLUMN IF NOT EXISTS position_observed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS observed_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE music_statuses
  ADD CONSTRAINT music_statuses_duration_check
    CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 86400000),
  ADD CONSTRAINT music_statuses_position_check
    CHECK (position_ms IS NULL OR position_ms BETWEEN 0 AND 86400000);
