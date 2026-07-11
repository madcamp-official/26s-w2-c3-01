CREATE TABLE lounge_buildings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(120) NOT NULL,
  address VARCHAR(240),
  google_place_id VARCHAR(160) UNIQUE,
  point geometry(Point, 4326) NOT NULL,
  radius_m INTEGER NOT NULL CHECK (radius_m BETWEEN 30 AND 2000),
  category VARCHAR(40) NOT NULL DEFAULT 'BUILDING',
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX lounge_buildings_point_idx ON lounge_buildings USING GIST (point);

CREATE TABLE building_lounges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  building_id UUID NOT NULL UNIQUE REFERENCES lounge_buildings(id) ON DELETE CASCADE,
  title VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE building_lounge_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  building_lounge_id UUID NOT NULL REFERENCES building_lounges(id) ON DELETE CASCADE,
  last_point geometry(Point, 4326) NOT NULL,
  accuracy_meters REAL,
  outside_count INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  UNIQUE(user_id, building_lounge_id)
);
CREATE INDEX building_lounge_sessions_active_idx ON building_lounge_sessions(building_lounge_id, active, expires_at);

CREATE TABLE sub_lounges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  building_lounge_id UUID NOT NULL REFERENCES building_lounges(id) ON DELETE CASCADE,
  creator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(80) NOT NULL,
  style VARCHAR(80),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sub_lounges_main_idx ON sub_lounges(building_lounge_id, active, created_at DESC);

CREATE TABLE sub_lounge_members (
  sub_lounge_id UUID NOT NULL REFERENCES sub_lounges(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  active BOOLEAN NOT NULL DEFAULT true,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(sub_lounge_id, user_id)
);

CREATE TABLE sub_lounge_listening_statuses (
  sub_lounge_id UUID NOT NULL REFERENCES sub_lounges(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  track_title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  album_art_url TEXT,
  is_playing BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(sub_lounge_id, user_id)
);

CREATE TABLE sub_lounge_recommendation_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sub_lounge_id UUID NOT NULL REFERENCES sub_lounges(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  track_title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  message VARCHAR(240),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sub_lounge_cards_room_idx ON sub_lounge_recommendation_cards(sub_lounge_id, created_at DESC);

CREATE TABLE sub_lounge_card_reactions (
  card_id UUID NOT NULL REFERENCES sub_lounge_recommendation_cards(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reaction_type VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(card_id, user_id, reaction_type)
);

WITH inserted AS (
  INSERT INTO lounge_buildings(name, address, google_place_id, point, radius_m, category)
  VALUES
    ('Pangyo Department Store', 'Pangyo sample place', 'sample-pangyo-department-store', ST_SetSRID(ST_MakePoint(127.1120, 37.3927), 4326), 300, 'SHOPPING_MALL'),
    ('Campus Music Hall', 'Campus sample place', 'sample-campus-music-hall', ST_SetSRID(ST_MakePoint(127.1048, 37.4019), 4326), 180, 'VENUE')
  ON CONFLICT (google_place_id) DO NOTHING
  RETURNING id, name
)
INSERT INTO building_lounges(building_id, title)
SELECT id, name || ' Main Lounge'
FROM inserted
ON CONFLICT (building_id) DO NOTHING;
