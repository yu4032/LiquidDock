package com.hellovoid.liquiddock;

/** Fail-closed ownership handoff for the Launcher shortcut-menu popup material. */
final class ShortcutPopupMaterialHandoffPolicy {
    private ShortcutPopupMaterialHandoffPolicy() {}

    static boolean mayReplaceVendorMaterial(boolean sharedSessionLive, boolean sinkAttached) {
        return sharedSessionLive && sinkAttached;
    }
}
