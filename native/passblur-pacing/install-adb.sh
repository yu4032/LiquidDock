#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-adb}"
MODULE_ID="liquiddock_passblur_pacing"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_TMP="/data/local/tmp/$MODULE_ID"
REMOTE_MOD="/data/adb/modules/$MODULE_ID"

"$ADB" shell su -c "rm -rf '$REMOTE_TMP' '$REMOTE_MOD'; mkdir -p '$REMOTE_TMP' '$REMOTE_MOD'"
for file in module.prop interval_ms post-fs-data.sh uninstall.sh; do
    "$ADB" push "$SCRIPT_DIR/$file" "$REMOTE_TMP/$file" >/dev/null
done
"$ADB" shell su -c "cp -f '$REMOTE_TMP/'* '$REMOTE_MOD/'; chmod 0755 '$REMOTE_MOD/post-fs-data.sh' '$REMOTE_MOD/uninstall.sh'; chmod 0644 '$REMOTE_MOD/module.prop' '$REMOTE_MOD/interval_ms'; rm -rf '$REMOTE_TMP'"
echo "Installed $MODULE_ID. Reboot is required so the property is applied before PassBlur instances are created."
