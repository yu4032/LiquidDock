package com.hellovoid.liquiddock;

/** Folder content progress owns the backdrop fade; the outgoing icon plate's fade does not. */
final class OpenFolderBackdropPolicy {
    private OpenFolderBackdropPolicy() {}
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

    /** Geometric progress of the *actual* transformed OEM plate. Scale and
     * translation may move on different Folme curves; derive the fade from
     * the same root-space rectangle which drives the per-frame glass sample.
     * Return NaN if the plate cannot be compared to known native endpoints.
     */
    static float nativeFrameProgress(OpenFolderGlassGeometry.Frame closed,
                                     OpenFolderGlassGeometry.Frame expanded,
                                     OpenFolderGlassGeometry.Frame observed) {
        if (closed == null || expanded == null || observed == null) return Float.NaN;
        float cw = closed.right - closed.left, ew = expanded.right - expanded.left;
        float ow = observed.right - observed.left;
        float ch = closed.bottom - closed.top, eh = expanded.bottom - expanded.top;
        float oh = observed.bottom - observed.top;
        if (Float.isFinite(ow) && Math.abs(ew - cw) > 0.01f) {
            return backdropOpacity((ow - cw) / (ew - cw));
        }
        if (Float.isFinite(oh) && Math.abs(eh - ch) > 0.01f) {
            return backdropOpacity((oh - ch) / (eh - ch));
        }
        float cx = (closed.left + closed.right) * .5f;
        float ex = (expanded.left + expanded.right) * .5f;
        float ox = (observed.left + observed.right) * .5f;
        if (Float.isFinite(ox) && Math.abs(ex - cx) > 0.01f) {
            return backdropOpacity((ox - cx) / (ex - cx));
        }
        float cy = (closed.top + closed.bottom) * .5f;
        float ey = (expanded.top + expanded.bottom) * .5f;
        float oy = (observed.top + observed.bottom) * .5f;
        return Float.isFinite(oy) && Math.abs(ey - cy) > 0.01f
                ? backdropOpacity((oy - cy) / (ey - cy)) : Float.NaN;
    }

    // Prepared desktop-blur texture is stored as an FBO; the logical
    // fullscreen output quad uses the opposite vertical UV convention.
    static float backdropSampleY(float outputY) { return 1f - outputY; }

    static float backdropOpacity(float progress) {
        return Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
    }

    /** Keep the same immutable wallpaper sample while its composition
     * ownership is transferred to OEM Recents. The original OEM gesture ratio
     * drives both the HWUI rounded outline and the alpha of the cached image.
     * Missing progress is NOT an animation endpoint: callers must preserve
     * the existing presentation until a real native sample arrives.
     */
    static float recentsBackdropAlpha(float nativeRatio) {
        return Float.isFinite(nativeRatio)
                ? 1f - backdropOpacity(nativeRatio) : Float.NaN;
    }

    /** 0 = expanded folder material, 1 = final resident Workspace material.
     * The glass itself remains opaque at the icon endpoint; a separate
     * ready-frame handoff owns removal of the temporary surface.
     */
    static float workspaceGlassMix(float nativeOpenProgress) {
        float t = 1f - backdropOpacity(nativeOpenProgress);
        return t * t * (3f - 2f * t);
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
