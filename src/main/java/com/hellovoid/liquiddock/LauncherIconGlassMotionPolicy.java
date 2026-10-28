package com.hellovoid.liquiddock;

/** Pure geometry: reuse the OEM animated corner radius and its own progress. */
final class LauncherIconGlassMotionPolicy {
    private LauncherIconGlassMotionPolicy() {}

    static float iconWeight(float nativeProgress, boolean closeToHome) {
        if (!Float.isFinite(nativeProgress)) return 0f;
        float p = Math.max(0f, Math.min(1f, nativeProgress));
        return closeToHome ? p : 1f - p;
    }

    /** f3 from Launcher 4.50 FloatingIcon.update is already in screen pixels. */
    static float radius(float nativeScreenRadius, float glassScreenRadius,
                        float nativeProgress, boolean closeToHome) {
        if (!Float.isFinite(nativeScreenRadius) || nativeScreenRadius < 0f
                || !Float.isFinite(glassScreenRadius) || glassScreenRadius < 0f) {
            return Float.NaN;
        }
        float weight = iconWeight(nativeProgress, closeToHome);
        return nativeScreenRadius + (glassScreenRadius - nativeScreenRadius) * weight;
    }

    static float localRadius(float screenRadius, float nativeScale) {
        if (!Float.isFinite(screenRadius) || !Float.isFinite(nativeScale)
                || nativeScale <= 0f) return Float.NaN;
        return Math.max(0f, screenRadius / nativeScale);
    }

    /** Match the Layer2 showSurfaceControl() ratio exactly. */
    static float layerScale(float rectangleWidth, float rectangleHeight,
                            float bufferWidth, float bufferHeight) {
        if (!Float.isFinite(rectangleWidth) || !Float.isFinite(rectangleHeight)
                || !Float.isFinite(bufferWidth) || !Float.isFinite(bufferHeight)
                || Math.min(Math.min(rectangleWidth, rectangleHeight),
                            Math.min(bufferWidth, bufferHeight)) <= 0f) {
            return Float.NaN;
        }
        return rectangleHeight > rectangleWidth
                ? rectangleHeight / bufferHeight : rectangleWidth / bufferWidth;
    }
}
