ALTER TABLE user_follows
  ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX user_follows_id_idx ON user_follows(id);
