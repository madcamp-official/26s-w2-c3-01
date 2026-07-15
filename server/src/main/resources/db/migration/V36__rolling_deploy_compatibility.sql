-- Keep the removed fields as inert compatibility columns while an older task can
-- briefly coexist with the new task during an ECS rolling deployment.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS mood_tags VARCHAR(240) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS melody_alias_mood VARCHAR(40);

ALTER TABLE profile_signature_tracks
  ADD COLUMN IF NOT EXISTS mood_tags VARCHAR(240) NOT NULL DEFAULT '';

ALTER TABLE taste_track_catalog
  ADD COLUMN IF NOT EXISTS mood_tags VARCHAR(240) NOT NULL DEFAULT '';
