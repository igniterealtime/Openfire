#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
if [ -z "${1:-}" ]; then
  echo "Usage: run-tests.sh <tag>" >&2
  echo "Available tags: demoboot, sasl2" >&2
  exit 1
fi
INCLUDE_TAGS="$1"

# TODO: research F-Droid APIs to resolve the latest Conversations APK dynamically. v2.19.15 is 4217303 (x86_64) and 4217304 (arm64-v8a).
# For now, when updating the version number here, update the cache key in conversationstest-action/action.yml
if [ ! -f conversations.apk ]; then
  echo "Downloading Conversations APK..."
  curl -fL \
    --progress-bar \
    --retry 5 \
    --retry-all-errors \
    --retry-delay 2 \
    --connect-timeout 15 \
    --max-time 300 \
    -o conversations.apk \
    https://f-droid.org/repo/eu.siacs.conversations_4217303.apk
else
  echo "Using cached Conversations APK."
fi

echo "Installing Conversations APK..."
adb install -r conversations.apk

echo "Starting sidecar API..."
trap '"$SCRIPT_DIR/stop-sidecar-api.sh"' EXIT
"$SCRIPT_DIR/start-sidecar-api.sh"

echo "Running Maestro tests (tags: $INCLUDE_TAGS)..."
maestro test --include-tags "$INCLUDE_TAGS" "$REPO_ROOT/build/ci/conversations/flows/" \
  || { RC=$?; adb logcat -d conversations:V '*:S' > adb_logcat.log 2>&1 || true; exit $RC; }
