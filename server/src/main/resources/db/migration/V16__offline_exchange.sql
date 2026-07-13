CREATE TABLE offline_credentials (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  public_subject UUID NOT NULL UNIQUE,
  device_public_key TEXT NOT NULL,
  display_alias VARCHAR(40) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  server_signature TEXT NOT NULL,
  revoked_at TIMESTAMPTZ,
  UNIQUE(user_id, device_public_key, expires_at)
);
CREATE INDEX offline_credentials_user_expiry_idx ON offline_credentials(user_id, expires_at DESC);

CREATE TABLE offline_exchange_events (
  id UUID PRIMARY KEY,
  exchange_id UUID NOT NULL,
  participant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  credential_id UUID NOT NULL REFERENCES offline_credentials(id),
  peer_credential_id UUID NOT NULL REFERENCES offline_credentials(id),
  sent_card_json JSONB NOT NULL,
  received_card_json JSONB NOT NULL,
  device_occurred_at TIMESTAMPTZ NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  protocol_version INTEGER NOT NULL,
  record_signature TEXT NOT NULL,
  verification_state VARCHAR(24) NOT NULL DEFAULT 'UNCONFIRMED',
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  verified_at TIMESTAMPTZ,
  UNIQUE(exchange_id, participant_user_id)
);
CREATE INDEX offline_exchange_participant_time_idx
  ON offline_exchange_events(participant_user_id, device_occurred_at DESC);
CREATE INDEX offline_exchange_match_idx
  ON offline_exchange_events(exchange_id, credential_id, peer_credential_id);
