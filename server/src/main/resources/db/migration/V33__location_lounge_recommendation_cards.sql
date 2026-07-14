CREATE TABLE location_lounge_recommendation_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_room_id UUID NOT NULL REFERENCES location_lounge_chat_rooms(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_card_id UUID NOT NULL,
  track_title VARCHAR(160) NOT NULL,
  artist_name VARCHAR(160) NOT NULL,
  message VARCHAR(240),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(sender_id, client_card_id)
);

CREATE INDEX location_lounge_recommendation_cards_room_idx
  ON location_lounge_recommendation_cards(chat_room_id, created_at DESC);

CREATE TABLE location_lounge_card_reactions (
  card_id UUID NOT NULL REFERENCES location_lounge_recommendation_cards(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reaction_type VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(card_id, user_id, reaction_type)
);
