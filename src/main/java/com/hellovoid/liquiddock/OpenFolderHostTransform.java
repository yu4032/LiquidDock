package com.hellovoid.liquiddock;

/**
 * Axis-aligned affine transform of the OPEN_FOLDER output host into Launcher
 * root coordinates. Native Recents may change its parent transform without
 * changing the cached EGL buffer. The output layer must cancel only that
 * host transform; the Prismal lens geometry is projected separately.
 *
 * This does not claim to undo a WindowManager/Task SurfaceControl leash
 * transform, which cannot be observed from a Java View matrix.
 */
final class OpenFolderHostTransform {
    private static final float MAX_SHEAR = 0.002f;
    final float x, y, scaleX, scaleY;

    private OpenFolderHostTransform(float x, float y, float scaleX, float scaleY) {
        this.x = x;
        this.y = y;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /** [originX,originY, unitX.x,unitX.y, unitY.x,unitY.y]. */
    static OpenFolderHostTransform fromBasis(float[] b) {
        if (b == null || b.length < 6) return null;
        for (int i = 0; i < 6; i++) if (!Float.isFinite(b[i])) return null;
        float sx = b[2] - b[0];
        float sy = b[5] - b[1];
        if (sx < .2f || sy < .2f || sx > 4f || sy > 4f
                || Math.abs(b[3] - b[1]) > MAX_SHEAR
                || Math.abs(b[4] - b[0]) > MAX_SHEAR) return null;
        return new OpenFolderHostTransform(b[0], b[1], sx, sy);
    }

    /** View pivot=0. The layer is root-sized, but its parent may be scaled. */
    float inverseScaleX() { return 1f / scaleX; }
    float inverseScaleY() { return 1f / scaleY; }
    float inverseTranslationX(int childLeft) { return -childLeft - x / scaleX; }
    float inverseTranslationY(int childTop) { return -childTop - y / scaleY; }

    /** Reproject the vendor's initial native expanded material into the current
     * root-space host geometry; screen-space sampling stays 1:1.
     */
    OpenFolderGlassGeometry.Frame project(
            OpenFolderGlassGeometry.Frame original, OpenFolderHostTransform baseline) {
        if (original == null || baseline == null) return null;
        float sx = scaleX / baseline.scaleX;
        float sy = scaleY / baseline.scaleY;
        float tx = x - sx * baseline.x;
        float ty = y - sy * baseline.y;
        return new OpenFolderGlassGeometry.Frame(
                original.left * sx + tx, original.top * sy + ty,
                original.right * sx + tx, original.bottom * sy + ty,
                original.radius * Math.min(sx, sy));
    }

    boolean approximatelyEquals(OpenFolderHostTransform other) {
        return other != null
                && Math.abs(x - other.x) < .1f
                && Math.abs(y - other.y) < .1f
                && Math.abs(scaleX - other.scaleX) < .001f
                && Math.abs(scaleY - other.scaleY) < .001f;
    }
}
