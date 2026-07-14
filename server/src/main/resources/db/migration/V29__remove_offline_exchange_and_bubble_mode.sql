-- Remove the retired offline exchange and bubble-mode persistence model.
DROP TABLE IF EXISTS offline_exchange_events CASCADE;
DROP TABLE IF EXISTS offline_credentials CASCADE;

ALTER TABLE user_privacy_settings
  DROP CONSTRAINT IF EXISTS user_privacy_exchange_insights_visibility_check,
  DROP CONSTRAINT IF EXISTS user_privacy_bubble_presence_visibility_check,
  DROP COLUMN IF EXISTS offline_exchange_enabled,
  DROP COLUMN IF EXISTS exchange_insights_visibility,
  DROP COLUMN IF EXISTS bubble_presence_visibility;
