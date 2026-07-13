-- Legacy media columns intentionally remain for one compatibility release.
-- They can be dropped after every running server task uses avatar_seed.
ALTER TABLE users
  ADD COLUMN avatar_seed VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text;
