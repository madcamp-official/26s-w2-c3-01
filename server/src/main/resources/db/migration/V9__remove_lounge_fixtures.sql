UPDATE building_lounges lounge
SET active = false
FROM lounge_buildings building
WHERE lounge.building_id = building.id
  AND (
    building.google_place_id LIKE 'sample-%'
    OR building.google_place_id LIKE 'test-fixture-%'
  );

UPDATE lounge_buildings
SET active = false, updated_at = now()
WHERE google_place_id LIKE 'sample-%'
   OR google_place_id LIKE 'test-fixture-%';
