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

    /** The OPEN_FOLDER output carries a full-screen wallpaper under the lens.
     * During native Recents gesture, reveal that backdrop through ONE rounded
     * silhouette: full root at gesture=0, opened-folder material at gesture=1.
     * Use the OEM gesture ratio, never a wall-clock animation or icon size.
     */
    static Frame recentsBackdropMask(Frame expanded, int rootWidth, int rootHeight, float nativeRatio) {
        if (expanded == null || rootWidth <= 0 || rootHeight <= 0
                || !valid(expanded, rootWidth, rootHeight)) return null;
        float p = Float.isFinite(nativeRatio)
                ? Math.max(0f, Math.min(1f, nativeRatio)) : 1f;
        float radius = Math.max(0f, Math.min(expanded.radius,
                Math.min(expanded.right - expanded.left, expanded.bottom - expanded.top) * .5f));
        return new Frame(
                expanded.left * p,
                expanded.top * p,
                rootWidth + (expanded.right - rootWidth) * p,
                rootHeight + (expanded.bottom - rootHeight) * p,
                radius * p);
    }

    /** Native FolderCling CLOSE owns progress: expanded=1, icon=0.
     * The temporary OPEN_FOLDER TextureView also contains a full-screen
     * wallpaper backdrop. Morph its visible background from the whole root
     * into the *desktop source folder* round-rect on the SAME reverse-spring
     * progress already used to position the Prismal glass lens.
     *
     * This is distinct from Recents: Recents ends at the expanded panel,
     * while native CLOSE ends at the Workspace icon. Neither needs a
     * second timer or hardcoded 1x1/2x2 folder geometry.
     */
    static Frame closingBackdropMask(Frame desktopIcon, int rootWidth, int rootHeight,
                                     float nativeOpenProgress) {
        if (desktopIcon == null || !valid(desktopIcon, rootWidth, rootHeight)
                || rootWidth <= 0 || rootHeight <= 0) return null;
        float p = Float.isFinite(nativeOpenProgress)
                ? Math.max(0f, Math.min(1f, nativeOpenProgress)) : 1f;
        float t = 1f - p; // 0: full screen, 1: desktop icon
        float radius = Math.max(0f, Math.min(desktopIcon.radius,
                Math.min(desktopIcon.right - desktopIcon.left,
                        desktopIcon.bottom - desktopIcon.top) * .5f));
        return new Frame(
                desktopIcon.left * t,
                desktopIcon.top * t,
                rootWidth + (desktopIcon.right - rootWidth) * t,
                rootHeight + (desktopIcon.bottom - rootHeight) * t,
                radius * t);
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
