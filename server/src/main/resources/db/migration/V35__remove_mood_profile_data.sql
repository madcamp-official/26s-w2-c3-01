ALTER TABLE users
  DROP COLUMN IF EXISTS mood_tags,
  DROP COLUMN IF EXISTS melody_alias_mood;

ALTER TABLE profile_signature_tracks
  DROP COLUMN IF EXISTS mood_tags;

ALTER TABLE taste_track_catalog
  DROP COLUMN IF EXISTS mood_tags;

INSERT INTO taste_model_versions(model_version, algorithm_version, status, calibration, activated_at)
VALUES (
  'taste-bootstrap-v3-normalized',
  'HYBRID_SIAMESE_V3_NORMALIZED',
  'ACTIVE',
  '{"normalization":"cosine_to_0_100","center":50,"dimensions":128,"minimumEvidence":3,"signals":["genre","artist","track","listen"]}'::jsonb,
  now()
)
ON CONFLICT(model_version) DO NOTHING;

UPDATE taste_model_versions
SET status='RETIRED'
WHERE model_version='taste-bootstrap-v2';

INSERT INTO taste_embedding_jobs(user_id, reason)
SELECT id, 'MOOD_REMOVED' FROM users
ON CONFLICT(user_id) DO UPDATE SET
  reason=excluded.reason,
  requested_at=now(),
  claimed_at=null,
  last_error=null;
