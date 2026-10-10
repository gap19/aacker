#!/usr/bin/env bash
# Build the release APK and install it on the watch over Wi-Fi ADB.
# Usage: scripts/install.sh [IP:PORT]   (default: the only connected device)
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$root/scripts/android-env.sh"
cd "$root"
./gradlew -q :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
adb ${1:+-s "$1"} install -r app/build/outputs/apk/release/app-release.apk
