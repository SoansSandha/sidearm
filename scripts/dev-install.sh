#!/usr/bin/env bash
# Builds the debug APK with a fresh versionCode and installs it on the connected phone.
# Vector hot-reloads the module inside system_server whenever versionCode changes, so most hook
# changes take effect without a reboot. Check with: adb logcat -s Sidearm
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew :app:assembleDebug -Psidearm.versionCode="$(date +%s)" --console=plain -q
# -d allows going back to a lower versionCode (debug builds only), e.g. after a plain assembleDebug.
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
