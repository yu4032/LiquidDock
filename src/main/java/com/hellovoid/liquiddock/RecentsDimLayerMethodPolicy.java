package com.hellovoid.liquiddock;

/** Accept only OEM task-transition dim entrypoints, never global Surface alpha setters. */
final class RecentsDimLayerMethodPolicy {
    private RecentsDimLayerMethodPolicy() {}

    static boolean isDimMethod(String name, Class<?>[] parameters) {
        return "dim".equals(name) && parameters != null && parameters.length == 3
                && parameters[0] == float.class
                && parameters[2] == boolean.class
                && isKnownSurfaceType(parameters[1].getName());
    }

    static boolean isKnownSurfaceType(String name) {
        return "com.android.systemui.shared.recents.system.SurfaceControlCompat".equals(name)
                || "android.view.SurfaceControl".equals(name);
    }
}
