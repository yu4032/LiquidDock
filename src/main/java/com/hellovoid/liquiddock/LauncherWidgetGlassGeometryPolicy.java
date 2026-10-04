package com.hellovoid.liquiddock;

/** Android-free widget glass width policy for HOME workspace offset changes. */
final class LauncherWidgetGlassGeometryPolicy {
    private LauncherWidgetGlassGeometryPolicy() {}

    static boolean isHorizontalSpanOwnerClassName(String className) {
        return "com.miui.home.launcher.LauncherWidgetView".equals(className)
                || "com.miui.home.launcher.maml.MaMlWidgetView".equals(className);
    }

    static int horizontalWidthDelta(
            int spanX, int currentFrameWidth, int zeroOffsetFrameWidth) {
        if (spanX <= 1 || currentFrameWidth <= 0 || zeroOffsetFrameWidth <= 0) return 0;
        return currentFrameWidth - zeroOffsetFrameWidth;
    }
}
