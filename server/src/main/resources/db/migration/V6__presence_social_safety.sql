ALTER TABLE user_privacy_settings
  ADD COLUMN IF NOT EXISTS discoverability_scope VARCHAR(24) NOT NULL DEFAULT 'NEARBY',
  ADD COLUMN IF NOT EXISTS music_visibility VARCHAR(24) NOT NULL DEFAULT 'TITLE_ARTIST',
  ADD COLUMN IF NOT EXISTS discovery_radius_meters INTEGER NOT NULL DEFAULT 300,
  ADD COLUMN IF NOT EXISTS allow_reactions BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE user_privacy_settings
  ADD CONSTRAINT user_privacy_discoverability_scope_check
    CHECK (discoverability_scope IN ('NEARBY', 'MUTUALS', 'HIDDEN')),
  ADD CONSTRAINT user_privacy_music_visibility_check
    CHECK (music_visibility IN ('TITLE_ARTIST', 'MUTUALS', 'HIDDEN')),
  ADD CONSTRAINT user_privacy_radius_check
    CHECK (discovery_radius_meters BETWEEN 50 AND 2000);

UPDATE user_privacy_settings
SET discoverability_scope = CASE WHEN discoverable THEN 'NEARBY' ELSE 'HIDDEN' END,
    music_visibility = CASE WHEN share_music THEN 'TITLE_ARTIST' ELSE 'HIDDEN' END;

CREATE TABLE user_follows (
  follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  followed_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (follower_id, followed_id),
  CHECK (follower_id <> followed_id)
);
CREATE INDEX user_follows_followed_idx ON user_follows(followed_id);

CREATE TABLE user_blocks (
  id UUID PRIMARY KEY,
  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (blocker_id, blocked_id),
  CHECK (blocker_id <> blocked_id)
);
CREATE INDEX user_blocks_blocked_idx ON user_blocks(blocked_id);

CREATE TABLE direct_chat_pairs (
  first_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  second_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  room_id UUID NOT NULL UNIQUE REFERENCES chat_rooms(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (first_user_id, second_user_id),
  CHECK (first_user_id::text < second_user_id::text)
);

CREATE TABLE user_reports (
  id UUID PRIMARY KEY,
  reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reported_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  request_id UUID NOT NULL,
  reason VARCHAR(32) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (reporter_id, request_id),
  CHECK (reporter_id <> reported_id),
  CHECK (reason IN ('SPAM', 'HARASSMENT', 'HATE', 'SEXUAL_CONTENT', 'IMPERSONATION', 'OTHER'))
);
CREATE INDEX user_reports_review_idx ON user_reports(status, created_at);

-- 여러 서버 인스턴스에서도 동일하게 집행되는 고정 윈도우 제한입니다.
CREATE TABLE action_rate_limits (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  action VARCHAR(32) NOT NULL,
  window_started_at TIMESTAMPTZ NOT NULL,
  request_count INTEGER NOT NULL,
  PRIMARY KEY (user_id, action)
);
