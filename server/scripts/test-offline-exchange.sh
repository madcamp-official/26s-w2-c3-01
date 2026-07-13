#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080}"
stamp="$(date +%s)-$$"
password='OfflineTest1234'
tmp="$(mktemp -d)"
token_a=''
token_b=''

cleanup() {
  if [[ -n "$token_a" ]]; then
    curl -sf -X DELETE -H "Authorization: Bearer $token_a" "$base_url/api/v1/auth/account" >/dev/null || true
  fi
  if [[ -n "$token_b" ]]; then
    curl -sf -X DELETE -H "Authorization: Bearer $token_b" "$base_url/api/v1/auth/account" >/dev/null || true
  fi
  rm -rf "$tmp"
}
trap cleanup EXIT

canonical() {
  local output='' part length
  for part in "$@"; do
    length="$(LC_ALL=C printf '%s' "$part" | wc -c | tr -d ' ')"
    output="${output}${length}:${part}"
  done
  printf '%s' "$output"
}

signup() {
  local email="$1" alias="$2"
  jq -cn --arg email "$email" --arg password "$password" --arg alias "$alias" \
    '{email:$email,password:$password,passwordConfirmation:$password,displayName:$alias}' |
    curl -sf -H 'Content-Type: application/json' -d @- "$base_url/api/v1/auth/signup"
}

issue_credential() {
  local token="$1" key_file="$2"
  local public_key
  public_key="$(openssl pkey -in "$key_file" -pubout -outform DER | base64 | tr -d '\n')"
  jq -cn --arg key "$public_key" '{devicePublicKey:$key}' |
    curl -sf -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      -d @- "$base_url/api/v1/offline-credentials"
}

signup_a="$(signup "offline-a-$stamp@example.com" 'Offline A')"
signup_b="$(signup "offline-b-$stamp@example.com" 'Offline B')"
token_a="$(jq -r .accessToken <<<"$signup_a")"
token_b="$(jq -r .accessToken <<<"$signup_b")"

openssl ecparam -name prime256v1 -genkey -noout -out "$tmp/a.pem"
openssl ecparam -name prime256v1 -genkey -noout -out "$tmp/a2.pem"
openssl ecparam -name prime256v1 -genkey -noout -out "$tmp/b.pem"
credential_a="$(issue_credential "$token_a" "$tmp/a.pem")"
credential_a2="$(issue_credential "$token_a" "$tmp/a2.pem")"
credential_b="$(issue_credential "$token_b" "$tmp/b.pem")"

subject_a="$(jq -r .publicSubject <<<"$credential_a")"
subject_a2="$(jq -r .publicSubject <<<"$credential_a2")"
subject_b="$(jq -r .publicSubject <<<"$credential_b")"
[[ "$subject_a" == "$subject_a2" ]]
[[ "$subject_a" != "$subject_b" ]]

credential_id_a="$(jq -r .credentialId <<<"$credential_a")"
credential_id_a2="$(jq -r .credentialId <<<"$credential_a2")"
credential_id_b="$(jq -r .credentialId <<<"$credential_b")"
card_a="$(jq -cn '{displayAlias:"Offline A",trackTitle:"Alpha",trackArtist:"Artist A",melodyAlias:"C6 · E6",genreTags:["Indie","Pop"],moodTags:["Bright"]}')"
card_b="$(jq -cn '{displayAlias:"Offline B",trackTitle:"Beta",trackArtist:"Artist B",melodyAlias:"A5 · B5",genreTags:["Jazz"],moodTags:["Calm"]}')"
canonical_card_a="$(canonical 'Offline A' 'Alpha' 'Artist A' 'C6 · E6' 'Indie,Pop' 'Bright')"
canonical_card_b="$(canonical 'Offline B' 'Beta' 'Artist B' 'A5 · B5' 'Jazz' 'Calm')"
pair_a="$(canonical "$credential_id_a" "$canonical_card_a")"
pair_b="$(canonical "$credential_id_b" "$canonical_card_b")"
if [[ "$credential_id_a" < "$credential_id_b" ]]; then
  payload="$(canonical "$pair_a" "$pair_b")"
else
  payload="$(canonical "$pair_b" "$pair_a")"
fi
payload_hash="$(printf '%s' "$payload" | shasum -a 256 | awk '{print $1}')"
exchange_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
occurred_at="$(($(date +%s) * 1000))"
record_a="$(canonical "$exchange_id" "$credential_id_a" "$credential_id_b" "$payload_hash" '1' "$occurred_at")"
record_b="$(canonical "$exchange_id" "$credential_id_b" "$credential_id_a" "$payload_hash" '1' "$occurred_at")"
signature_a="$(printf '%s' "$record_a" | openssl dgst -sha256 -sign "$tmp/a.pem" | base64 | tr -d '\n')"
signature_b="$(printf '%s' "$record_b" | openssl dgst -sha256 -sign "$tmp/b.pem" | base64 | tr -d '\n')"

item_a="$(jq -cn --arg eid "$exchange_id" --arg cid "$credential_id_a" --arg peer "$credential_id_b" \
  --arg sent "$card_a" --arg received "$card_b" --argjson at "$occurred_at" --arg hash "$payload_hash" \
  --arg sig "$signature_a" \
  '{exchangeId:$eid,credentialId:$cid,peerCredentialId:$peer,sentCardJson:$sent,receivedCardJson:$received,deviceOccurredAt:$at,payloadHash:$hash,protocolVersion:1,recordSignature:$sig}')"
item_b="$(jq -cn --arg eid "$exchange_id" --arg cid "$credential_id_b" --arg peer "$credential_id_a" \
  --arg sent "$card_b" --arg received "$card_a" --argjson at "$occurred_at" --arg hash "$payload_hash" \
  --arg sig "$signature_b" \
  '{exchangeId:$eid,credentialId:$cid,peerCredentialId:$peer,sentCardJson:$sent,receivedCardJson:$received,deviceOccurredAt:$at,payloadHash:$hash,protocolVersion:1,recordSignature:$sig}')"

first="$(jq -cn --argjson item "$item_a" '{items:[$item]}' |
  curl -sf -H 'Content-Type: application/json' -H "Authorization: Bearer $token_a" \
    -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$(jq -r '.[0].state' <<<"$first")" == 'UNCONFIRMED' ]]

invalid_item="$(jq -cn --argjson item "$item_a" '$item + {recordSignature:"invalid"}')"
invalid_status="$(jq -cn --argjson item "$invalid_item" '{items:[$item]}' |
  curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token_a" -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$invalid_status" == '400' ]]

changed_at="$((occurred_at + 1))"
changed_record="$(canonical "$exchange_id" "$credential_id_a" "$credential_id_b" "$payload_hash" '1' "$changed_at")"
changed_signature="$(printf '%s' "$changed_record" | openssl dgst -sha256 -sign "$tmp/a.pem" | base64 | tr -d '\n')"
changed_item="$(jq -cn --argjson item "$item_a" --argjson at "$changed_at" --arg sig "$changed_signature" \
  '$item + {deviceOccurredAt:$at,recordSignature:$sig}')"
changed_status="$(jq -cn --argjson item "$changed_item" '{items:[$item]}' |
  curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token_a" -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$changed_status" == '400' ]]

self_item="$(jq -cn --arg eid "$(uuidgen | tr '[:upper:]' '[:lower:]')" --arg cid "$credential_id_a" \
  --arg peer "$credential_id_a2" --arg sent "$card_a" --argjson at "$occurred_at" \
  '{exchangeId:$eid,credentialId:$cid,peerCredentialId:$peer,sentCardJson:$sent,receivedCardJson:$sent,deviceOccurredAt:$at,payloadHash:"invalid",protocolVersion:1,recordSignature:"invalid"}')"
self_status="$(jq -cn --argjson item "$self_item" '{items:[$item]}' |
  curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token_a" -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$self_status" == '400' ]]

second="$(jq -cn --argjson item "$item_b" '{items:[$item]}' |
  curl -sf -H 'Content-Type: application/json' -H "Authorization: Bearer $token_b" \
    -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$(jq -r '.[0].state' <<<"$second")" == 'VERIFIED' ]]

repeat="$(jq -cn --argjson item "$item_a" '{items:[$item]}' |
  curl -sf -H 'Content-Type: application/json' -H "Authorization: Bearer $token_a" \
    -d @- "$base_url/api/v1/offline-exchanges/batch")"
[[ "$(jq -r '.[0].state' <<<"$repeat")" == 'VERIFIED' ]]

history_a="$(curl -sf -H "Authorization: Bearer $token_a" "$base_url/api/v1/offline-exchanges")"
history_b="$(curl -sf -H "Authorization: Bearer $token_b" "$base_url/api/v1/offline-exchanges")"
[[ "$(jq 'length' <<<"$history_a")" -eq 1 ]]
[[ "$(jq -r '.[0].verificationState' <<<"$history_b")" == 'VERIFIED' ]]
profile_a="$(curl -sf -H "Authorization: Bearer $token_a" "$base_url/api/v1/me")"
[[ "$(jq -r '.offlineExchangeCount' <<<"$profile_a")" -eq 1 ]]
[[ "$(jq -r '.offlineExchangeGenres | sort | join(",")' <<<"$profile_a")" == 'Jazz' ]]
[[ "$(jq -r '.offlineExchangeMoods | sort | join(",")' <<<"$profile_a")" == 'Calm' ]]
profile_b="$(curl -sf -H "Authorization: Bearer $token_b" "$base_url/api/v1/me")"
handle_a="$(jq -r .profileHandle <<<"$profile_a")"
handle_b="$(jq -r .profileHandle <<<"$profile_b")"
[[ "$handle_a" =~ ^[a-z0-9_]{3,32}$ ]]
[[ "$handle_b" =~ ^[a-z0-9_]{3,32}$ ]]
[[ "$handle_a" != "$handle_b" ]]
[[ "$(jq -r '.stats.verifiedExchangeCount' <<<"$profile_a")" -eq 1 ]]
[[ "$(jq -r '.stats.uniqueExchangeUserCount' <<<"$profile_a")" -eq 1 ]]
[[ "$(jq -r '.tasteFingerprint.genres[0].label' <<<"$profile_a")" == 'Jazz' ]]
[[ "$(jq -r '.tasteFingerprint.genres[0].ratio' <<<"$profile_a")" == '1' ]]

melody_alias="$(jq -cn '{id:"night-signal",notes:["A4","C5","E5"],tone:"전자음",mood:"몽환",tempo:112}')"
profile_a="$(curl -sf -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $token_a" \
  -d "$melody_alias" "$base_url/api/v1/me/melody-alias")"
[[ "$(jq -r '.melodyAlias.id' <<<"$profile_a")" == 'night-signal' ]]
[[ "$(jq -r '.melodyAlias.notes | join(",")' <<<"$profile_a")" == 'A4,C5,E5' ]]

privacy="$(jq -cn '{discoverable:true,shareMusic:true,offlineExchangeEnabled:false}')"
profile_a="$(curl -sf -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $token_a" \
  -d "$privacy" "$base_url/api/v1/me/privacy")"
[[ "$(jq -r '.offlineExchangeEnabled' <<<"$profile_a")" == 'false' ]]

exchange_profile="$(curl -sf -H "Authorization: Bearer $token_a" \
  "$base_url/api/v1/profiles/exchange/$exchange_id")"
[[ "$(jq -r .profileHandle <<<"$exchange_profile")" == "$handle_b" ]]
[[ "$(jq -r .sharedVerifiedExchangeCount <<<"$exchange_profile")" -eq 1 ]]
[[ "$(jq -r .relationship <<<"$exchange_profile")" == 'NONE' ]]

followed="$(curl -sf -X PUT -H "Authorization: Bearer $token_a" \
  "$base_url/api/v1/profiles/$handle_b/follow")"
[[ "$(jq -r .following <<<"$followed")" == 'true' ]]
public_b="$(curl -sf -H "Authorization: Bearer $token_a" "$base_url/api/v1/profiles/$handle_b")"
[[ "$(jq -r .following <<<"$public_b")" == 'true' ]]
[[ "$(jq -r .stats.followerCount <<<"$public_b")" -eq 1 ]]
following_a="$(curl -sf -H "Authorization: Bearer $token_a" "$base_url/api/v1/me/following")"
[[ "$(jq -r '.[0].profileHandle' <<<"$following_a")" == "$handle_b" ]]
curl -sf -X DELETE -H "Authorization: Bearer $token_a" \
  "$base_url/api/v1/profiles/$handle_b/follow" >/dev/null

curl -sf -X DELETE -H "Authorization: Bearer $token_a" \
  "$base_url/api/v1/offline-exchanges/$exchange_id" >/dev/null
[[ "$(curl -sf -H "Authorization: Bearer $token_a" "$base_url/api/v1/offline-exchanges" | jq 'length')" -eq 0 ]]

curl -sf -X DELETE -H "Authorization: Bearer $token_a" "$base_url/api/v1/auth/account" >/dev/null
token_a=''
curl -sf -X DELETE -H "Authorization: Bearer $token_b" "$base_url/api/v1/auth/account" >/dev/null
token_b=''

echo 'offline exchange HTTP integration: PASS'
echo 'credential trust, pair verification, public profile, taste, stable handle, follow, melody alias, privacy, record/account delete: PASS'
