ALTER TABLE offline_exchange_events
  DROP CONSTRAINT IF EXISTS offline_exchange_events_credential_id_fkey;

ALTER TABLE offline_exchange_events
  DROP CONSTRAINT IF EXISTS offline_exchange_events_peer_credential_id_fkey;

ALTER TABLE offline_exchange_events
  ADD CONSTRAINT offline_exchange_events_credential_id_fkey
  FOREIGN KEY (credential_id) REFERENCES offline_credentials(id) ON DELETE CASCADE;

ALTER TABLE offline_exchange_events
  ADD CONSTRAINT offline_exchange_events_peer_credential_id_fkey
  FOREIGN KEY (peer_credential_id) REFERENCES offline_credentials(id) ON DELETE CASCADE;
