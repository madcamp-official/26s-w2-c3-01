CREATE TABLE nearby_reactions (
  id UUID PRIMARY KEY,
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_reaction_id UUID NOT NULL,
  reaction_type VARCHAR(32) NOT NULL,
  track_title VARCHAR(160),
  track_artist VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(sender_id, client_reaction_id),
  CHECK (sender_id <> recipient_id),
  CHECK (reaction_type IN ('LIKE', 'SAME_TASTE', 'GREAT_PICK', 'LISTEN_TOGETHER')),
  CHECK (
    (track_title IS NULL AND track_artist IS NULL)
    OR (track_title IS NOT NULL AND track_artist IS NOT NULL)
  )
);

CREATE INDEX nearby_reactions_recipient_created_idx
  ON nearby_reactions(recipient_id, created_at DESC);

CREATE INDEX nearby_reactions_track_idx
  ON nearby_reactions(track_title, track_artist)
  WHERE track_title IS NOT NULL;
