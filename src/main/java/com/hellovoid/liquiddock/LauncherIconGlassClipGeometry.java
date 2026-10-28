package com.hellovoid.liquiddock;

/** Icon-local bounds use the same offset and corner cap as the glass renderer. */
final class LauncherIconGlassClipGeometry {
    static final class Result {
        final float left, top, right, bottom, radius;
        Result(float left, float top, float right, float bottom, float radius) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.radius = radius;
        }
    }
    static Result resolve(float left, float top, float right, float bottom,
                          float offset, float configuredRadius, float nativeRadius) {
        float[] b = LauncherGlassBoundsPolicy.apply(left, top, right, bottom, offset);
        float radius = configuredRadius > 0f ? configuredRadius : nativeRadius;
        return new Result(b[0], b[1], b[2], b[3],
                LauncherGlassBoundsPolicy.capRadius(radius, b[2] - b[0], b[3] - b[1]));
    }
    /** Clip any drawable bleed to either side of the icon, without touching text below. */
    static float[] iconRow(float viewWidth, float iconTop, float iconBottom) {
        return new float[]{-1f, Math.max(0f, iconTop - 1f),
                Math.max(0f, viewWidth) + 1f, iconBottom + 1f};
    }
    private LauncherIconGlassClipGeometry() {}
}
