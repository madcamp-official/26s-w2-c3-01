ALTER TABLE users
  ADD COLUMN profile_music_start_seconds REAL;

ALTER TABLE users
  ADD CONSTRAINT users_profile_music_start_seconds_check
  CHECK (profile_music_start_seconds IS NULL OR profile_music_start_seconds BETWEEN 0 AND 25);
