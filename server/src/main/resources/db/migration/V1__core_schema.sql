CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY,
  email CITEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  profile_color VARCHAR(16) NOT NULL DEFAULT '#6750A4',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_privacy_settings (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  discoverable BOOLEAN NOT NULL DEFAULT true,
  share_music BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE presence_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_session_id VARCHAR(128) NOT NULL,
  nearby_handle VARCHAR(48) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_session_id)
);
CREATE INDEX presence_sessions_active_idx ON presence_sessions (expires_at);

-- 위치 이력 테이블이 아니라 세션당 최신값 1개만 보관합니다.
CREATE TABLE current_locations (
  session_id UUID PRIMARY KEY REFERENCES presence_sessions(id) ON DELETE CASCADE,
  point geometry(Point, 4326) NOT NULL,
  accuracy_meters REAL,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX current_locations_point_idx ON current_locations USING GIST (point);

CREATE TABLE music_statuses (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  track_title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  album_art_url TEXT,
  source_type VARCHAR(32) NOT NULL,
  is_playing BOOLEAN NOT NULL DEFAULT true,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id)
);

CREATE TABLE chat_rooms (
  id UUID PRIMARY KEY,
  type VARCHAR(24) NOT NULL DEFAULT 'DIRECT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE chat_room_members (
  room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  last_read_message_id UUID,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(room_id, user_id)
);
CREATE TABLE chat_messages (
  id UUID PRIMARY KEY,
  room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_message_id UUID,
  content VARCHAR(1000) NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(sender_id, client_message_id)
);
CREATE INDEX chat_messages_room_sent_idx ON chat_messages(room_id, sent_at DESC);

CREATE TABLE lounges (
  id UUID PRIMARY KEY,
  title VARCHAR(80) NOT NULL,
  description VARCHAR(240) NOT NULL,
  theme VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE lounge_cards (
  id UUID PRIMARY KEY,
  lounge_id UUID NOT NULL REFERENCES lounges(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  track_title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE lounge_votes (
  lounge_id UUID NOT NULL REFERENCES lounges(id) ON DELETE CASCADE,
  voter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vote_type VARCHAR(32) NOT NULL,
  target_key VARCHAR(160) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(lounge_id, voter_id, vote_type)
);
