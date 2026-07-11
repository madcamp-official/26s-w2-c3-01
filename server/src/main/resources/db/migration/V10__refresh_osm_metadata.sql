UPDATE lounge_buildings
SET address = '주소 정보 없음',
    updated_at = now() - interval '25 hours'
WHERE google_place_id LIKE 'osm-%'
  AND address = 'OpenStreetMap building footprint';
