ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_object_key TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_mime_type VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_music_object_key TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_music_mime_type VARCHAR(80);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_music_description VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_music_updated_at TIMESTAMPTZ;
