#!/usr/bin/env bash
set -euo pipefail

# Git Bash rewrites /data/... to a Windows path unless MSYS path conversion is disabled.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT_DIR/docs/perf/workspace-app-home-perfetto.pbtxt"
REMOTE_CONFIG="/data/local/tmp/ld_workspace_home.pbtxt"
REMOTE_TRACE="/data/misc/perfetto-traces/ld_workspace_home.trace"
LABEL="${1:-glass-on-7widgets}"
OUTPUT="${2:-ld_workspace_home_${LABEL}.trace}"

echo "Make sure the intended APK and glass setting are already active."
echo "For the next 30 seconds: App->HOME via icon, App->HOME via widget,"
echo "Recents->HOME, then a short period with another App covering Launcher."
echo "Write down the approximate order/time of each interaction."
adb get-state >/dev/null
adb push "$CONFIG" "$REMOTE_CONFIG" >/dev/null
adb shell perfetto --txt -c "$REMOTE_CONFIG" -o "$REMOTE_TRACE"
adb pull "$REMOTE_TRACE" "$OUTPUT" >/dev/null
echo "Trace saved: $OUTPUT"
echo "Compare with identically collected glass-off and diagnostic runs."
