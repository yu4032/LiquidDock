package com.hellovoid.liquiddock;

/** Screen-space transform for a native taskbar body; its measurement and vertical motion stay native. */
final class WorkstationNativeTaskbarGeometry {
    private WorkstationNativeTaskbarGeometry() {}

    static float scaleX(float current, int width, float screenWidth, int displayWidth) {
        if (width <= 0 || screenWidth <= .001f || displayWidth <= 0
                || !Float.isFinite(current) || !Float.isFinite(screenWidth)) return current;
        return current * displayWidth / screenWidth;
    }

    static float translationX(float current, float screenLeft, float parentScale) {
        if (!Float.isFinite(screenLeft) || !Float.isFinite(parentScale)
                || parentScale <= .001f) return current;
        return current - screenLeft / parentScale;
    }
}
