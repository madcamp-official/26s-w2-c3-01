#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
  echo "adb not found at $ADB" >&2
  exit 1
fi

CONNECTED_DEVICES=()
while IFS= read -r serial; do
  CONNECTED_DEVICES+=("$serial")
done < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
FIRST_DEVICE="${1:-${CONNECTED_DEVICES[0]:-}}"
SECOND_DEVICE="${2:-${CONNECTED_DEVICES[1]:-}}"

if [[ -z "$FIRST_DEVICE" || -z "$SECOND_DEVICE" || "$FIRST_DEVICE" == "$SECOND_DEVICE" ]]; then
  echo "usage: $0 [first-adb-serial] [second-adb-serial]" >&2
  echo "two distinct connected Android devices are required" >&2
  exit 1
fi

for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  if ! "$ADB" -s "$serial" get-state 2>/dev/null | grep -qx device; then
    echo "device is not ready: $serial" >&2
    exit 1
  fi
done

cd "$ROOT_DIR"
./gradlew assembleDebug assembleDebugAndroidTest

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
for serial in "$FIRST_DEVICE" "$SECOND_DEVICE"; do
  "$ADB" -s "$serial" install -r "$APP_APK" >/dev/null
  "$ADB" -s "$serial" install -r "$TEST_APK" >/dev/null
done

RESULT_DIR="$(mktemp -d)"
trap 'rm -rf "$RESULT_DIR"' EXIT

run_role() {
  local serial="$1"
  local role="$2"
  local output="$3"
  "$ADB" -s "$serial" shell am instrument -w -r \
    -e class com.example.myapplication.NearbyTwoDeviceExchangeTest \
    -e nearbyRole "$role" \
    com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner \
    >"$output" 2>&1
}

run_role "$FIRST_DEVICE" initiator "$RESULT_DIR/initiator.txt" &
FIRST_PID=$!
run_role "$SECOND_DEVICE" responder "$RESULT_DIR/responder.txt" &
SECOND_PID=$!

FIRST_STATUS=0
SECOND_STATUS=0
wait "$FIRST_PID" || FIRST_STATUS=$?
wait "$SECOND_PID" || SECOND_STATUS=$?

cat "$RESULT_DIR/initiator.txt"
cat "$RESULT_DIR/responder.txt"

if [[ "$FIRST_STATUS" -ne 0 || "$SECOND_STATUS" -ne 0 ]] ||
  ! grep -q 'OK (1 test)' "$RESULT_DIR/initiator.txt" ||
  ! grep -q 'OK (1 test)' "$RESULT_DIR/responder.txt"; then
  echo "Nearby two-device instrumentation failed" >&2
  exit 1
fi

value_from() {
  local key="$1"
  local file="$2"
  awk -F= -v key="$key" '$1 ~ key "$" { gsub(/\r/, "", $2); print $2; exit }' "$file"
}

FIRST_DIGITS="$(value_from nearbyAuthenticationDigits "$RESULT_DIR/initiator.txt")"
SECOND_DIGITS="$(value_from nearbyAuthenticationDigits "$RESULT_DIR/responder.txt")"
FIRST_EXCHANGE="$(value_from nearbyExchangeId "$RESULT_DIR/initiator.txt")"
SECOND_EXCHANGE="$(value_from nearbyExchangeId "$RESULT_DIR/responder.txt")"
FIRST_HASH="$(value_from nearbyPayloadHash "$RESULT_DIR/initiator.txt")"
SECOND_HASH="$(value_from nearbyPayloadHash "$RESULT_DIR/responder.txt")"

if [[ -z "$FIRST_DIGITS" || "$FIRST_DIGITS" != "$SECOND_DIGITS" ||
      -z "$FIRST_EXCHANGE" || "$FIRST_EXCHANGE" != "$SECOND_EXCHANGE" ||
      -z "$FIRST_HASH" || "$FIRST_HASH" != "$SECOND_HASH" ]]; then
  echo "Nearby peers did not agree on authentication digits, exchange id, and payload hash" >&2
  exit 1
fi

echo "Nearby two-device exchange: PASS"
echo "authentication digits: $FIRST_DIGITS"
echo "exchange id: $FIRST_EXCHANGE"
echo "payload hash: $FIRST_HASH"
