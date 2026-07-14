-- Fixture lounges from the initial schema are not part of real Wi-Fi lounge discovery.
-- Cascading foreign keys remove their building lounge and any dependent test data.
DELETE FROM lounge_buildings
WHERE google_place_id LIKE 'sample-%'
   OR google_place_id LIKE 'test-fixture-%';
