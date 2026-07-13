ALTER TABLE users
  ADD COLUMN IF NOT EXISTS profile_handle VARCHAR(32)
    DEFAULT ('listener_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 12)),
  ADD COLUMN IF NOT EXISTS melody_alias_id VARCHAR(80),
  ADD COLUMN IF NOT EXISTS melody_alias_notes JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS melody_alias_tone VARCHAR(40),
  ADD COLUMN IF NOT EXISTS melody_alias_mood VARCHAR(40),
  ADD COLUMN IF NOT EXISTS melody_alias_tempo INTEGER;

UPDATE users
SET profile_handle = 'listener_' || substr(replace(id::text, '-', ''), 1, 12)
WHERE profile_handle IS NULL OR profile_handle !~ '^[a-z0-9_]{3,32}$';

ALTER TABLE users
  ALTER COLUMN profile_handle SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS users_profile_handle_unique_idx
  ON users (lower(profile_handle));

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_profile_handle_format_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_profile_handle_format_check
      CHECK (profile_handle ~ '^[a-z0-9_]{3,32}$');
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_melody_alias_notes_array_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_melody_alias_notes_array_check
      CHECK (jsonb_typeof(melody_alias_notes) = 'array');
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'users_melody_alias_tempo_check'
  ) THEN
    ALTER TABLE users ADD CONSTRAINT users_melody_alias_tempo_check
      CHECK (melody_alias_tempo IS NULL OR melody_alias_tempo BETWEEN 40 AND 240);
  END IF;
END $$;

ALTER TABLE user_privacy_settings
  ADD COLUMN IF NOT EXISTS offline_exchange_enabled BOOLEAN NOT NULL DEFAULT true;
