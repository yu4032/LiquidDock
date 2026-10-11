package com.hellovoid.liquiddock;

/**
 * Conservative root-local clip for HWUI's final composite of the fullscreen transparent
 * TextureView. Never resize/reposition the EGL buffer: PassBlur and wallpaper coordinates
 * remain rooted at exactly the same origin.
 *
 * <p>Only a stationary Workspace with valid, completely known glass bounds may clip.
 * Moving/transitioning scenes must use the entire TextureView to avoid trailing clipped glass.
 */
final class LauncherGlassCompositingBounds {
    static final int GUARD_PX = 20;
    // A clip close to fullscreen is not worth an extra HWUI scissor operation.
    static final int MIN_SAVING_PERCENT = 15;

    int left;
    int top;
    int right;
    int bottom;
    boolean valid;

    void reset() {
        left = top = right = bottom = 0;
        valid = false;
    }

    /** Each visible node can extend outside the window during native spring/scroll motions. */
    boolean include(float centerX, float centerY, float width, float height) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0 || height <= 0) {
            return false;
        }
        float halfWidth = width * 0.5f, halfHeight = height * 0.5f;
        float l = centerX - halfWidth - GUARD_PX;
        float t = centerY - halfHeight - GUARD_PX;
        float r = centerX + halfWidth + GUARD_PX;
        float b = centerY + halfHeight + GUARD_PX;
        // Reject unrepresentable/overly-large geometry rather than creating a narrow mask.
        if (!Float.isFinite(l) || !Float.isFinite(t)
                || !Float.isFinite(r) || !Float.isFinite(b)
                || Math.max(Math.abs(l), Math.abs(r)) > 100000f
                || Math.max(Math.abs(t), Math.abs(b)) > 100000f) return false;
        int x0 = (int) Math.floor(l), y0 = (int) Math.floor(t);
        int x1 = (int) Math.ceil(r), y1 = (int) Math.ceil(b);
        if (!valid) {
            left = x0; top = y0; right = x1; bottom = y1; valid = true;
        } else {
            left = Math.min(left, x0);
            top = Math.min(top, y0);
            right = Math.max(right, x1);
            bottom = Math.max(bottom, y1);
        }
        return true;
    }

    /** Return true only when there is substantial coverage reduction and no unknown node. */
    boolean finish(int rootWidth, int rootHeight) {
        if (!valid || rootWidth <= 0 || rootHeight <= 0) return false;
        left = clamp(left, rootWidth);
        right = clamp(right, rootWidth);
        top = clamp(top, rootHeight);
        bottom = clamp(bottom, rootHeight);
        if (right <= left || bottom <= top) return false;
        long clipPixels = (long) (right - left) * (bottom - top);
        long screenPixels = (long) rootWidth * rootHeight;
        return clipPixels * 100 <= screenPixels * (100 - MIN_SAVING_PERCENT);
    }

    long area() {
        return valid ? (long) (right - left) * (bottom - top) : 0L;
    }

    private static int clamp(int x, int maximum) {
        return Math.max(0, Math.min(maximum, x));
    }
}
