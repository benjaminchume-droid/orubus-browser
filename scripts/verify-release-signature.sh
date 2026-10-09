#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Usage: $0 path/to/release.apk" >&2
  exit 2
fi

APKSIGNER="${APKSIGNER:-}"
if [[ -z "$APKSIGNER" ]]; then
  if command -v apksigner >/dev/null 2>&1; then
    APKSIGNER="$(command -v apksigner)"
  elif [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/build-tools" ]]; then
    APKSIGNER="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT/build-tools" ]]; then
    APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
  fi
fi

if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner not found. Install Android SDK build-tools or set APKSIGNER." >&2
  exit 1
fi

"$APKSIGNER" verify --verbose --print-certs "$APK"
echo
echo "Signature verification completed. Compare the reported SHA-256 certificate digest with your offline release record."
