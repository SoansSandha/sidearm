#!/usr/bin/env bash
# Signs a release APK with Sidearm's release key. CI only: the key lives in the GitHub "release"
# environment's secrets, never on a PC, and this runs in a job that runs no build code (ADR 0009).
#
# Usage: scripts/sign-apk.sh <unsigned.apk> <signed.apk>
#
# Reads SIDEARM_KEYSTORE_BASE64 (the PKCS12 keystore, base64), SIDEARM_KEYSTORE_PASSWORD and
# SIDEARM_KEY_ALIAS from the environment. Uses the build-tools directory in SIDEARM_BUILD_TOOLS, or
# else the newest one in ANDROID_HOME. The password reaches apksigner through the environment, never
# the command line. Prints only the certificate's subject and SHA-256 digest, which are public.
set -euo pipefail

usage="usage: scripts/sign-apk.sh <unsigned.apk> <signed.apk>"
in="${1:?$usage}"
out="${2:?$usage}"
for name in SIDEARM_KEYSTORE_BASE64 SIDEARM_KEYSTORE_PASSWORD SIDEARM_KEY_ALIAS; do
  if [ -z "${!name:-}" ]; then
    echo "$name isn't set. Add it to the \"release\" environment's secrets (CONTRIBUTING.md, Release signing)." >&2
    exit 1
  fi
done

build_tools="${SIDEARM_BUILD_TOOLS:-}"
if [ -z "$build_tools" ]; then
  build_tools="$(find "${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
fi
[ -x "$build_tools/apksigner" ] || { echo "No apksigner in '$build_tools'" >&2; exit 1; }

umask 077
work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sidearm-sign.XXXXXX")"
trap 'rm -rf "$work"' EXIT
# Secrets pasted or piped on Windows can carry CRLFs, which base64 -d rejects.
tr -d '\r\n[:blank:]' <<<"$SIDEARM_KEYSTORE_BASE64" | base64 -d >"$work/release.p12"

# AGP already aligns the APK; aligning again is cheap and makes the input's state not matter.
"$build_tools/zipalign" -f -p 4 "$in" "$work/aligned.apk"
"$build_tools/apksigner" sign --ks "$work/release.p12" --ks-key-alias "$SIDEARM_KEY_ALIAS" \
  --ks-pass env:SIDEARM_KEYSTORE_PASSWORD --key-pass env:SIDEARM_KEYSTORE_PASSWORD \
  --v4-signing-enabled false --out "$out" "$work/aligned.apk"   # v4 (.idsig) is only for adb's incremental installs
"$build_tools/apksigner" verify --print-certs "$out" | grep -E '^Signer #1 certificate (DN|SHA-256 digest): '
