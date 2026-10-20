package com.hellovoid.liquiddock;

/** Root-space bounds from the native animator; missing handoff observations retain one valid frame. */
final class OpenFolderGlassGeometry {
    static final class Frame {
        final float left, top, right, bottom, radius;
        Frame(float left, float top, float right, float bottom, float radius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
        }
    }

    private Frame current;
    private int width, height;

    Frame acceptAnimation(Frame observed, int rootWidth, int rootHeight) {
        if (width != rootWidth || height != rootHeight) {
            current = null;
            width = rootWidth;
            height = rootHeight;
        }
        if (rootWidth <= 0 || rootHeight <= 0) return null;
        if (observed == null || !valid(observed, rootWidth, rootHeight)) return current;
        float radius = Math.max(0f, Math.min(observed.radius,
                Math.min(observed.right - observed.left, observed.bottom - observed.top) * 0.5f));
        current = radius == observed.radius ? observed : new Frame(
                observed.left, observed.top, observed.right, observed.bottom, radius);
        return current;
    }

    Frame acceptPanel(Frame observed, int rootWidth, int rootHeight) {
        return acceptAnimation(observed, rootWidth, rootHeight);
    }

    Frame acceptProgress(Frame closed, Frame panel, float progress, int rootWidth, int rootHeight) {
        if (closed == null || panel == null || !Float.isFinite(progress)
                || !valid(closed, rootWidth, rootHeight) || !valid(panel, rootWidth, rootHeight)) {
            return acceptAnimation(null, rootWidth, rootHeight);
        }
        float p = Math.max(0f, Math.min(1f, progress));
        return acceptAnimation(new Frame(
                closed.left + (panel.left - closed.left) * p,
                closed.top + (panel.top - closed.top) * p,
                closed.right + (panel.right - closed.right) * p,
                closed.bottom + (panel.bottom - closed.bottom) * p,
                closed.radius + (panel.radius - closed.radius) * p), rootWidth, rootHeight);
    }

    /** The native desktop folder icon uses a different style from the
     * expanded folder background. In particular, 1x1 must never inherit
     * LARGE_FOLDER_CORNER_RADIUS as its end-of-close geometry.
     */
    static float sourceIconRadiusPx(boolean smallFolder, float smallOverrideDp,
                                    float largeOverrideDp, float density,
                                    float nativeRadiusPx, float width, float height) {
        float fallback = Math.max(0f, Math.min(width, height) * .22f);
        return LauncherGlassCornerRadiusPolicy.resolve(
                smallFolder ? smallOverrideDp : largeOverrideDp,
                density, nativeRadiusPx, fallback);
    }

    Frame current() { return current; }
    void reset() { current = null; width = height = 0; }

    private static boolean valid(Frame frame, int width, int height) {
        return Float.isFinite(frame.left) && Float.isFinite(frame.top)
                && Float.isFinite(frame.right) && Float.isFinite(frame.bottom)
                && Float.isFinite(frame.radius)
                && frame.right > frame.left && frame.bottom > frame.top
                && frame.right > 0 && frame.bottom > 0
                && frame.left < width && frame.top < height;
    }
}
