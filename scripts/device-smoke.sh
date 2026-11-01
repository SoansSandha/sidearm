#!/usr/bin/env bash
# Plays injected power-press sequences on the connected phone and checks what Sidearm logged for
# each: a single press, a double, a triple, and a double whose third press comes too late.
#
# It never injects more than 3 presses in a row, and waits between sequences, because 5 fast
# presses start Emergency SOS when it's on. The phone needs a debug build with the feature armed
# (enabled with a target); a dry run is fine and changes nothing the presses do. Leave the phone
# locked or unlocked as you like: the checks read the module's decisions, not the screen.
# A failed check prints the module's log lines, which name the chosen app: don't paste them into
# issues or PRs.
#
# Usage: scripts/device-smoke.sh
set -euo pipefail

GAP_SECONDS=3 # between sequences; any gap over 0.3 s already resets stock's tap count
SETTLE_SECONDS=1.5 # after a sequence, for the 350 ms camera timer and the log to land
SLOW_THIRD_SECONDS=0.45 # past stock's 300 ms window and our 50 ms grace

MAX_PRESSES=3 # in any 2 s; 5 fast presses start Emergency SOS
PRESS_BUDGET_MS=2000

failures=0
budget_start_ms=0
budget_used=0

now_ms() { date +%s%3N; }

# Injects 1 to 3 power presses, 150 ms apart. Refuses anything that would put more than
# MAX_PRESSES into one PRESS_BUDGET_MS, across calls too.
press() {
  local count="$1" now
  now="$(now_ms)"
  if [ $((now - budget_start_ms)) -gt "$PRESS_BUDGET_MS" ]; then
    budget_start_ms="$now"
    budget_used=0
  fi
  if [ "$count" -lt 1 ] || [ $((budget_used + count)) -gt "$MAX_PRESSES" ]; then
    echo "Refusing to inject $count more presses: never more than $MAX_PRESSES in a row (Emergency SOS)." >&2
    exit 1
  fi
  budget_used=$((budget_used + count))
  local keys=""
  for _ in $(seq "$count"); do keys="$keys KEYCODE_POWER"; done
  adb shell "input keyevent --delay 150$keys"
}

phone_time() { adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }

# The module's decisions since phone time "$1", one per line, without logcat's prefix.
decisions_since() {
  adb logcat -d -v raw -T "$1" -s Sidearm:I 2>/dev/null | grep -v '^-----' | sed 's/^io\.github\.soanssandha\.sidearm: //'
}

# check <name> <log> <pattern that must match> [pattern that must not match]
check() {
  local name="$1" log="$2" must="$3" mustnt="${4:-}"
  if grep -qE "$must" <<<"$log" && { [ -z "$mustnt" ] || ! grep -qE "$mustnt" <<<"$log"; }; then
    echo "PASS  $name"
  else
    echo "FAIL  $name"
    sed 's/^/        /' <<<"${log:-(nothing logged)}"
    failures=$((failures + 1))
  fi
}

# run <name> <must> <mustnt> <command...>: plays one sequence and checks what it logged.
run() {
  local name="$1" must="$2" mustnt="$3"
  shift 3
  local since
  since="$(phone_time)"
  "$@"
  sleep "$SETTLE_SECONDS"
  check "$name" "$(decisions_since "$since")" "$must" "$mustnt"
  sleep "$GAP_SECONDS"
}

slow_third() {
  press 2
  sleep "$SLOW_THIRD_SECONDS"
  press 1
}

adb get-state >/dev/null || { echo "No phone connected." >&2; exit 1; }

run "single press is left to stock" \
  'PowerDown\(.*taps=1.*-> stock' 'CameraRequested' press 1
if [ "$failures" -gt 0 ]; then
  echo "Nothing to go on: is the feature armed (enabled with a target)? Presses are only logged then." >&2
  exit 1
fi
run "double press asks for the camera" \
  'CameraRequested' 'taps=3' press 2
run "triple press opens the target" \
  'taps=3.*ConsumePress.*LaunchTarget' '' press 3
run "a slow third press is a new single press" \
  'CameraRequested' 'ConsumePress' slow_third

if [ "$failures" -eq 0 ]; then echo "All passed."; else echo "$failures failed."; exit 1; fi
