ALTER TABLE sub_lounge_recommendation_cards
  ADD COLUMN client_card_id UUID;

UPDATE sub_lounge_recommendation_cards
SET client_card_id = gen_random_uuid()
WHERE client_card_id IS NULL;

ALTER TABLE sub_lounge_recommendation_cards
  ALTER COLUMN client_card_id SET NOT NULL;

CREATE UNIQUE INDEX sub_lounge_cards_sender_client_idx
  ON sub_lounge_recommendation_cards(sender_id, client_card_id);

UPDATE sub_lounge_card_reactions
SET reaction_type = 'LIKE'
WHERE reaction_type NOT IN ('LIKE', 'LOVE', 'FIRE', 'SAME_TASTE');

ALTER TABLE sub_lounge_card_reactions
  ADD CONSTRAINT sub_lounge_reaction_type_check
  CHECK (reaction_type IN ('LIKE', 'LOVE', 'FIRE', 'SAME_TASTE'));

CREATE INDEX sub_lounge_members_active_idx
  ON sub_lounge_members(sub_lounge_id, active, last_seen_at DESC);

CREATE INDEX sub_lounge_listening_active_idx
  ON sub_lounge_listening_statuses(sub_lounge_id, expires_at DESC);

CREATE TABLE sub_lounge_votes (
  sub_lounge_id UUID NOT NULL REFERENCES sub_lounges(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_key VARCHAR(24) NOT NULL
    CHECK (target_key IN ('CHILL', 'FOCUS', 'ENERGY')),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(sub_lounge_id, user_id)
);

CREATE INDEX sub_lounge_votes_room_idx
  ON sub_lounge_votes(sub_lounge_id, target_key);
