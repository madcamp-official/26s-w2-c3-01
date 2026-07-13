#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SERVER_URL="${E2E_SERVER_URL:-http://localhost:8080}"
FIRST_DEVICE="${1:-}"
SECOND_DEVICE="${2:-}"

if [[ -z "$FIRST_DEVICE" || -z "$SECOND_DEVICE" || "$FIRST_DEVICE" == "$SECOND_DEVICE" ]]; then
  echo "usage: $0 first-adb-serial second-adb-serial" >&2
  exit 1
fi
for command in "$ADB" curl jq; do
  if ! command -v "$command" >/dev/null 2>&1 && [[ ! -x "$command" ]]; then
    echo "required command not found: $command" >&2
    exit 1
  fi
done
curl -fsS "$SERVER_URL/actuator/health" >/dev/null

device_ready() {
  "$ADB" -s "$1" get-state 2>/dev/null | grep -qx device
}
for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  if ! device_ready "$serial"; then
    echo "device is not ready: $serial" >&2
    exit 1
  fi
done

RUN_ID="$(date +%s)-$$"
FIRST_EMAIL="physical-a-$RUN_ID@melody.local"
SECOND_EMAIL="physical-b-$RUN_ID@melody.local"
PASSWORD="E2ePass${RUN_ID//-/}"
RESULT_DIR="$(mktemp -d)"
FIRST_WIFI="$($ADB -s "$FIRST_DEVICE" shell settings get global wifi_on 2>/dev/null | tr -d '\r')"
SECOND_WIFI="$($ADB -s "$SECOND_DEVICE" shell settings get global wifi_on 2>/dev/null | tr -d '\r')"
FIRST_DATA="$($ADB -s "$FIRST_DEVICE" shell settings get global mobile_data 2>/dev/null | tr -d '\r')"
SECOND_DATA="$($ADB -s "$SECOND_DEVICE" shell settings get global mobile_data 2>/dev/null | tr -d '\r')"

set_radio() {
  local serial="$1"
  local service="$2"
  local state="$3"
  if device_ready "$serial"; then
    "$ADB" -s "$serial" shell svc "$service" "$state" >/dev/null 2>&1 || true
  fi
}

restore_networks() {
  set_radio "$FIRST_DEVICE" wifi "$( [[ "$FIRST_WIFI" == 1 ]] && echo enable || echo disable )"
  set_radio "$SECOND_DEVICE" wifi "$( [[ "$SECOND_WIFI" == 1 ]] && echo enable || echo disable )"
  set_radio "$FIRST_DEVICE" data "$( [[ "$FIRST_DATA" == 1 ]] && echo enable || echo disable )"
  set_radio "$SECOND_DEVICE" data "$( [[ "$SECOND_DATA" == 1 ]] && echo enable || echo disable )"
}

delete_account() {
  local email="$1"
  local payload token
  payload="$(jq -nc --arg email "$email" --arg password "$PASSWORD" '{email:$email,password:$password}')"
  token="$(curl -fsS -H 'Content-Type: application/json' -d "$payload" \
    "$SERVER_URL/api/v1/auth/login" 2>/dev/null | jq -r '.accessToken // empty')" || return 0
  if [[ -n "$token" ]]; then
    curl -fsS -X DELETE -H "Authorization: Bearer $token" \
      "$SERVER_URL/api/v1/auth/account" >/dev/null 2>&1 || true
  fi
}

cleanup() {
  set +e
  restore_networks
  delete_account "$FIRST_EMAIL"
  delete_account "$SECOND_EMAIL"
  for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
    if device_ready "$serial"; then
      "$ADB" -s "$serial" reverse --remove tcp:8080 >/dev/null 2>&1 || true
      "$ADB" -s "$serial" shell pm clear com.example.myapplication >/dev/null 2>&1 || true
      "$ADB" -s "$serial" shell pm clear com.example.myapplication.test >/dev/null 2>&1 || true
    fi
  done
  rm -rf "$RESULT_DIR"
}
trap cleanup EXIT INT TERM

assert_ok() {
  local output="$1"
  if ! grep -q 'OK (1 test)' "$output"; then
    cat "$output"
    echo "instrumentation failed: $output" >&2
    exit 1
  fi
}

value_from() {
  local key="$1"
  local file="$2"
  awk -F= -v key="$key" '$1 ~ key "$" { gsub(/\r/, "", $2); print $2; exit }' "$file"
}

install_test_apks() {
  local serial="$1"
  "$ADB" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  "$ADB" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
  "$ADB" -s "$serial" shell pm clear com.example.myapplication >/dev/null
  "$ADB" -s "$serial" shell pm clear com.example.myapplication.test >/dev/null
  "$ADB" -s "$serial" reverse tcp:8080 tcp:8080 >/dev/null
}

prepare_account() {
  local serial="$1"
  local role="$2"
  local email="$3"
  local output="$4"
  "$ADB" -s "$serial" shell am instrument -w -r \
    -e class 'com.example.myapplication.OfflineAccountPhysicalE2ETest#prepareLoggedInAccountAndOfflineCredential' \
    -e e2ePhase prepare -e e2eRole "$role" \
    -e e2eEmail "$email" -e e2ePassword "$PASSWORD" \
    com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner \
    >"$output" 2>&1
}

run_exchange() {
  local serial="$1"
  local role="$2"
  local output="$3"
  "$ADB" -s "$serial" shell am instrument -w -r \
    -e class 'com.example.myapplication.OfflineAccountPhysicalE2ETest#restoreOfflineAccountExchangeAndPersistToRoom' \
    -e e2ePhase exchange -e e2eRole "$role" \
    com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner \
    >"$output" 2>&1
}

run_sync() {
  local serial="$1"
  local role="$2"
  local output="$3"
  "$ADB" -s "$serial" shell am instrument -w -r \
    -e class 'com.example.myapplication.OfflineAccountPhysicalE2ETest#workManagerSyncsAndServerVerifiesProfile' \
    -e e2ePhase sync -e e2eRole "$role" \
    com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner \
    >"$output" 2>&1
}

cd "$ROOT_DIR"
./gradlew -PAPI_BASE_URL=http://localhost:8080 -PSTOMP_WS_URL=ws://localhost:8080/ws \
  assembleDebug assembleDebugAndroidTest

install_test_apks "$FIRST_DEVICE"
install_test_apks "$SECOND_DEVICE"
prepare_account "$FIRST_DEVICE" initiator "$FIRST_EMAIL" "$RESULT_DIR/prepare-a.txt"
prepare_account "$SECOND_DEVICE" responder "$SECOND_EMAIL" "$RESULT_DIR/prepare-b.txt"
assert_ok "$RESULT_DIR/prepare-a.txt"
assert_ok "$RESULT_DIR/prepare-b.txt"
echo "online account cache and server credential preparation: PASS"

for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  set_radio "$serial" wifi disable
  set_radio "$serial" data disable
  set_radio "$serial" bluetooth enable
done
sleep 3
for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  if "$ADB" -s "$serial" shell ping -c 1 -W 1 1.1.1.1 >/dev/null 2>&1; then
    echo "device still has internet connectivity: $serial" >&2
    exit 1
  fi
done
echo "both devices have no internet connectivity: PASS"

run_exchange "$FIRST_DEVICE" initiator "$RESULT_DIR/exchange-a.txt" &
FIRST_PID=$!
run_exchange "$SECOND_DEVICE" responder "$RESULT_DIR/exchange-b.txt" &
SECOND_PID=$!
FIRST_STATUS=0
SECOND_STATUS=0
wait "$FIRST_PID" || FIRST_STATUS=$?
wait "$SECOND_PID" || SECOND_STATUS=$?
if [[ "$FIRST_STATUS" -ne 0 || "$SECOND_STATUS" -ne 0 ]]; then
  cat "$RESULT_DIR/exchange-a.txt" "$RESULT_DIR/exchange-b.txt"
  echo "offline exchange instrumentation process failed" >&2
  exit 1
fi
assert_ok "$RESULT_DIR/exchange-a.txt"
assert_ok "$RESULT_DIR/exchange-b.txt"

FIRST_DIGITS="$(value_from e2eAuthenticationDigits "$RESULT_DIR/exchange-a.txt")"
SECOND_DIGITS="$(value_from e2eAuthenticationDigits "$RESULT_DIR/exchange-b.txt")"
FIRST_EXCHANGE="$(value_from e2eExchangeId "$RESULT_DIR/exchange-a.txt")"
SECOND_EXCHANGE="$(value_from e2eExchangeId "$RESULT_DIR/exchange-b.txt")"
FIRST_HASH="$(value_from e2ePayloadHash "$RESULT_DIR/exchange-a.txt")"
SECOND_HASH="$(value_from e2ePayloadHash "$RESULT_DIR/exchange-b.txt")"
FIRST_STATE="$(value_from e2eLocalSyncState "$RESULT_DIR/exchange-a.txt")"
SECOND_STATE="$(value_from e2eLocalSyncState "$RESULT_DIR/exchange-b.txt")"
if [[ -z "$FIRST_DIGITS" || "$FIRST_DIGITS" != "$SECOND_DIGITS" ||
      -z "$FIRST_EXCHANGE" || "$FIRST_EXCHANGE" != "$SECOND_EXCHANGE" ||
      -z "$FIRST_HASH" || "$FIRST_HASH" != "$SECOND_HASH" ||
      "$FIRST_STATE" != PENDING || "$SECOND_STATE" != PENDING ]]; then
  cat "$RESULT_DIR/exchange-a.txt" "$RESULT_DIR/exchange-b.txt"
  echo "offline peers or Room PENDING records do not agree" >&2
  exit 1
fi
echo "offline Nearby exchange and account-scoped Room persistence: PASS"

set_radio "$FIRST_DEVICE" wifi enable
set_radio "$SECOND_DEVICE" wifi enable
set_radio "$FIRST_DEVICE" data enable
set_radio "$SECOND_DEVICE" data enable
for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  "$ADB" -s "$serial" reverse tcp:8080 tcp:8080 >/dev/null
done
sleep 5

run_sync "$FIRST_DEVICE" initiator "$RESULT_DIR/sync-a.txt" &
FIRST_PID=$!
run_sync "$SECOND_DEVICE" responder "$RESULT_DIR/sync-b.txt" &
SECOND_PID=$!
FIRST_STATUS=0
SECOND_STATUS=0
wait "$FIRST_PID" || FIRST_STATUS=$?
wait "$SECOND_PID" || SECOND_STATUS=$?
if [[ "$FIRST_STATUS" -ne 0 || "$SECOND_STATUS" -ne 0 ]]; then
  cat "$RESULT_DIR/sync-a.txt" "$RESULT_DIR/sync-b.txt"
  echo "online sync instrumentation process failed" >&2
  exit 1
fi
assert_ok "$RESULT_DIR/sync-a.txt"
assert_ok "$RESULT_DIR/sync-b.txt"

FIRST_VERIFIED="$(value_from e2eVerifiedExchangeId "$RESULT_DIR/sync-a.txt")"
SECOND_VERIFIED="$(value_from e2eVerifiedExchangeId "$RESULT_DIR/sync-b.txt")"
FIRST_VERIFICATION="$(value_from e2eVerificationState "$RESULT_DIR/sync-a.txt")"
SECOND_VERIFICATION="$(value_from e2eVerificationState "$RESULT_DIR/sync-b.txt")"
FIRST_COUNT="$(value_from e2eProfileExchangeCount "$RESULT_DIR/sync-a.txt")"
SECOND_COUNT="$(value_from e2eProfileExchangeCount "$RESULT_DIR/sync-b.txt")"
if [[ "$FIRST_VERIFIED" != "$FIRST_EXCHANGE" || "$SECOND_VERIFIED" != "$FIRST_EXCHANGE" ||
      "$FIRST_VERIFICATION" != VERIFIED || "$SECOND_VERIFICATION" != VERIFIED ||
      "${FIRST_COUNT:-0}" -lt 1 || "${SECOND_COUNT:-0}" -lt 1 ]]; then
  cat "$RESULT_DIR/sync-a.txt" "$RESULT_DIR/sync-b.txt"
  echo "server verification or profile aggregation failed" >&2
  exit 1
fi

echo "WorkManager sync, server VERIFIED pair, and profile aggregation: PASS"
echo "authentication digits: $FIRST_DIGITS"
echo "exchange id: $FIRST_EXCHANGE"
echo "payload hash: $FIRST_HASH"
