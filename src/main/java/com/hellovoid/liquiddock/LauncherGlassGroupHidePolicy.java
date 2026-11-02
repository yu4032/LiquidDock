package com.hellovoid.liquiddock;

/** Group opacity excludes the animated FloatingIcon, which retains native visual ownership. */
final class LauncherGlassGroupHidePolicy {
    private LauncherGlassGroupHidePolicy() {}

    static float dockBackground(boolean enabled, boolean workspaceLaunchingAway,
            boolean dockLaunchingAway, float homeAlpha) {
        if (!enabled) return 1f;
        if (workspaceLaunchingAway || dockLaunchingAway) return 0f;
        return finiteAlpha(homeAlpha);
    }

    static boolean hideWholeWorkspaceOutput(boolean enabled, boolean localProxyActive) {
        // A local Workspace proxy shares this output; hide other nodes, never the whole texture.
        return enabled && !localProxyActive;
    }

    static boolean honorWorkspaceAncestorAlpha(boolean enabled, boolean localProxyActive) {
        // When hiding is disabled, native Workspace alpha must not inadvertently hide
        // the independent shared-glass output through each sibling node.
        return enabled && localProxyActive;
    }

    static float workspaceBackground(boolean enabled, boolean localProxyActive,
            boolean launchAway, float returningAlpha) {
        if (!enabled || !localProxyActive) return 1f;
        return launchAway ? 0f : finiteAlpha(returningAlpha);
    }

    private static float finiteAlpha(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
