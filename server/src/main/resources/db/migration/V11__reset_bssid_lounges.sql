-- V10 grouped Wi-Fi lounges by access-point BSSID. Reset those generated rows
-- before switching to SSID plus geographic clustering.
DELETE FROM lounge_buildings
WHERE category = 'WIFI';
