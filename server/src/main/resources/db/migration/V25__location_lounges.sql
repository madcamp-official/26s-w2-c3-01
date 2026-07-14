CREATE TABLE location_lounges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  center geometry(Point, 4326) NOT NULL,
  radius_m INTEGER NOT NULL DEFAULT 5 CHECK (radius_m IN (5, 10, 20)),
  current_user_count INTEGER NOT NULL DEFAULT 0 CHECK (current_user_count >= 0),
  created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'MERGING', 'DELETING', 'DELETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX location_lounges_center_idx ON location_lounges USING GIST(center);
CREATE INDEX location_lounges_active_idx ON location_lounges(status, created_at, id);

-- This is a derived cache, not lounge membership. It exists only to calculate enter/leave deltas.
CREATE TABLE location_lounge_presence_cache (
  lounge_id UUID NOT NULL REFERENCES location_lounges(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(lounge_id, user_id)
);

CREATE TABLE location_lounge_chat_rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lounge_id UUID NOT NULL REFERENCES location_lounges(id) ON DELETE RESTRICT,
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  title VARCHAR(80) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX location_lounge_chat_rooms_lounge_idx
  ON location_lounge_chat_rooms(lounge_id, status, created_at);

CREATE TABLE location_lounge_chat_members (
  chat_room_id UUID NOT NULL REFERENCES location_lounge_chat_rooms(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  active BOOLEAN NOT NULL DEFAULT true,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(chat_room_id, user_id)
);

CREATE TABLE location_lounge_chat_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_room_id UUID NOT NULL REFERENCES location_lounge_chat_rooms(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  client_message_id UUID NOT NULL,
  content VARCHAR(1000) NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(sender_id, client_message_id)
);
CREATE INDEX location_lounge_chat_messages_room_idx
  ON location_lounge_chat_messages(chat_room_id, sent_at DESC);
