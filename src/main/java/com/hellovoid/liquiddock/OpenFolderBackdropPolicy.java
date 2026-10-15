package com.hellovoid.liquiddock;

/** Folder content progress owns the backdrop fade; the outgoing icon plate's fade does not. */
final class OpenFolderBackdropPolicy {
    private OpenFolderBackdropPolicy() {}
    // Prepared blur uses Bitmap rows; the output quad uses FBO UVs.
    static float backdropSampleY(float outputY) { return 1f - outputY; }

    static float nativeProgress(float x, float y, float targetX, float targetY) {
        if (Float.isFinite(x) && Float.isFinite(targetX) && Math.abs(targetX) > .001f) return x / targetX;
        if (Float.isFinite(y) && Float.isFinite(targetY) && Math.abs(targetY) > .001f) return y / targetY;
        return Float.NaN;
    }
    /**
     * Same native geometric coordinate for opening (0 -> 1) and closing (1 -> 0).
     * Launcher 4.50 can open a folder almost without translation; its animator
     * then changes SCALE_X, not the View's measured width. Measure the native
     * Folme scale before falling back to size-based progress.
     */
    static float nativeVisualProgress(float x, float y, float targetX, float targetY,
                                      float scaleX, float expandedScaleX) {
        float translated = nativeProgress(x, y, targetX, targetY);
        if (Float.isFinite(translated)) return backdropOpacity(translated);
        if (Float.isFinite(scaleX) && Float.isFinite(expandedScaleX)
                && Math.abs(expandedScaleX - 1f) > .001f) {
            return backdropOpacity((scaleX - 1f) / (expandedScaleX - 1f));
        }
        return Float.NaN;
    }

    static float backdropOpacity(float progress) {
        return Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
    }

    static float openProgress(float sourceWidth, float targetWidth, float observedWidth,
                              float sourceHeight, float targetHeight, float observedHeight) {
        if (!Float.isFinite(sourceWidth) || !Float.isFinite(targetWidth) || !Float.isFinite(observedWidth)
                || !Float.isFinite(sourceHeight) || !Float.isFinite(targetHeight) || !Float.isFinite(observedHeight)) return 0f;
        float delta = targetWidth - sourceWidth;
        if (Math.abs(delta) > 0.001f) return backdropOpacity((observedWidth - sourceWidth) / delta);
        delta = targetHeight - sourceHeight;
        return Math.abs(delta) > 0.001f ? backdropOpacity((observedHeight - sourceHeight) / delta) : 1f;
    }
}
