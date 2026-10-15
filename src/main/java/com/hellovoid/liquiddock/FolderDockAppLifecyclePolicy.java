package com.hellovoid.liquiddock;

/** Pure guard for a synthetic folder-only Dock App lifecycle. */
final class FolderDockAppLifecyclePolicy {
    private FolderDockAppLifecyclePolicy() {}

    static boolean shouldEnter(String state) {
        return "HOME_STATE".equals(state);
    }

    static boolean shouldRestore(String state, boolean realAppLaunched, boolean launcherPaused) {
        return "APP_STATE".equals(state) && !realAppLaunched && !launcherPaused;
    }
}
