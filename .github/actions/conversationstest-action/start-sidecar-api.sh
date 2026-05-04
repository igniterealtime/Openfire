#!/usr/bin/env bash
set -euo pipefail

SIDECAR_VERSION="${SIDECAR_VERSION:-1.0.0}" # A released version of maestro-logcat-sidecar
WORK_DIR="${RUNNER_TEMP:-/tmp}" # Use GHA temp directory, else the OS temp directory
SIDECAR_JAR="$WORK_DIR/maestro-logcat-sidecar-${SIDECAR_VERSION}.jar"
SIDECAR_URL="https://github.com/Fishbowler/maestro-logcat-sidecar/releases/download/v${SIDECAR_VERSION}/maestro-logcat-sidecar-${SIDECAR_VERSION}.jar"
PID_FILE="$WORK_DIR/.sidecar.pid"
LOG_FILE="$WORK_DIR/sidecar.log"
LOGCAT_TAGS="${LOGCAT_TAGS:-conversations:V *:S}"

if [ ! -f "$SIDECAR_JAR" ]; then
    echo "Downloading maestro-logcat-sidecar v${SIDECAR_VERSION}..."
    curl -fsSL -o "$SIDECAR_JAR" "$SIDECAR_URL"
fi

LOGCAT_TAGS="$LOGCAT_TAGS" \
  java -jar "$SIDECAR_JAR" >"$LOG_FILE" 2>&1 &

SIDECAR_PID=$!
echo "$SIDECAR_PID" >"$PID_FILE"

for _ in $(seq 1 30); do
    if curl -sf "http://localhost:17777/health" >/dev/null 2>&1; then
        echo "Sidecar ready on port 17777"
        exit 0
    fi
    sleep 0.5
done

echo "ERROR: sidecar did not become ready within 15 seconds. Check $LOG_FILE" >&2
exit 1
