package com.hellovoid.liquiddock;

/** Track the Folme channels actually carrying the native folder reverse
 * spring. The source/expanded glass rectangles remain authoritative geometry;
 * these channels only provide a normalized progress between those endpoints.
 *
 * An unchanged translation property must not override a changing scale or
 * vice versa. All active channels are observed, and closing uses the most
 * contracted valid coordinate. No arbitrary time/distance animation.
 */
final class OpenFolderNativeCloseProgress {
    private boolean x, y, scale;
    private boolean captured;

    void capture(float tx, float ty, float sx, float endX, float endY, float endScaleX) {
        if (captured && hasChannels()) return;
        captured = true;
        x = channelActive(tx, endX);
        y = channelActive(ty, endY);
        scale = Float.isFinite(sx) && Float.isFinite(endScaleX)
                && Math.abs(endScaleX - 1f) > 0.01f
                && (sx - 1f) / (endScaleX - 1f) > 0.1f;
    }

    boolean hasChannels() { return x || y || scale; }

    float sample(float tx, float ty, float sx, float endX, float endY, float endScaleX) {
        float value = Float.NaN;
        if (x && valid(endX)) value = combine(value, tx / endX);
        if (y && valid(endY)) value = combine(value, ty / endY);
        if (scale && Float.isFinite(endScaleX)
                && Math.abs(endScaleX - 1f) > 0.01f) {
            value = combine(value, (sx - 1f) / (endScaleX - 1f));
        }
        return Float.isFinite(value)
                ? OpenFolderBackdropPolicy.backdropOpacity(value) : Float.NaN;
    }

    /** Rounded folder animation can scale the parent FolderIcon/plate rather
     * than the child ImageView. In that case the child translation and scale
     * channels remain constant even while the native plate visibly contracts.
     *
     * These native View bounds are ONLY a normalized motion sensor; they
     * never replace the authoritative source/expanded glass rectangles.
     * Ignore an unchanged or near-icon-sized plate to prevent false progress.
     */
    static float sampleVisualFrame(OpenFolderGlassGeometry.Frame desktop,
                                   OpenFolderGlassGeometry.Frame start,
                                   OpenFolderGlassGeometry.Frame current) {
        if (desktop == null || start == null || current == null) return Float.NaN;
        float startWidth = start.right - start.left;
        float iconWidth = desktop.right - desktop.left;
        float nowWidth = current.right - current.left;
        float sample = contractingAxis(iconWidth, startWidth, nowWidth,
                Math.max(8f, iconWidth * .15f));
        if (Float.isFinite(sample)) return sample;
        float startHeight = start.bottom - start.top;
        float iconHeight = desktop.bottom - desktop.top;
        float nowHeight = current.bottom - current.top;
        sample = contractingAxis(iconHeight, startHeight, nowHeight,
                Math.max(8f, iconHeight * .15f));
        if (Float.isFinite(sample)) return sample;

        // A translation-only Folme plate can have constant dimensions.
        float startX = (start.left + start.right) * .5f;
        float iconX = (desktop.left + desktop.right) * .5f;
        float currentX = (current.left + current.right) * .5f;
        sample = movingAxis(iconX, startX, currentX, 12f);
        if (Float.isFinite(sample)) return sample;
        float startY = (start.top + start.bottom) * .5f;
        float iconY = (desktop.top + desktop.bottom) * .5f;
        float currentY = (current.top + current.bottom) * .5f;
        return movingAxis(iconY, startY, currentY, 12f);
    }

    private static float contractingAxis(float icon, float start, float now, float minExpansion) {
        if (!Float.isFinite(icon) || !Float.isFinite(start) || !Float.isFinite(now)
                || start - icon < minExpansion || now <= 0f
                || now > start + Math.max(2f, (start - icon) * .04f)) return Float.NaN;
        if (start - now < Math.max(1f, (start - icon) * .01f)) return Float.NaN;
        return OpenFolderBackdropPolicy.backdropOpacity((now - icon) / (start - icon));
    }

    private static float movingAxis(float icon, float start, float now, float minimum) {
        if (!Float.isFinite(icon) || !Float.isFinite(start) || !Float.isFinite(now)
                || Math.abs(start - icon) < minimum
                || Math.abs(now - start) < Math.max(1f, Math.abs(start - icon) * .01f)) {
            return Float.NaN;
        }
        float ratio = (now - icon) / (start - icon);
        // Discard samples moving away from the destination; no guessed spring
        // extrapolation is allowed to hide the still-animating folder plate.
        return ratio < -.1f || ratio > 1.1f
                ? Float.NaN : OpenFolderBackdropPolicy.backdropOpacity(ratio);
    }

    String channelDescription() {
        return "x=" + x + " y=" + y + " scale=" + scale;
    }

    void reset() { x = y = scale = captured = false; }

    private static boolean valid(float target) {
        return Float.isFinite(target) && Math.abs(target) > 0.01f;
    }

    private static boolean channelActive(float current, float target) {
        return Float.isFinite(current) && valid(target)
                && current / target > 0.1f;
    }

    private static float combine(float current, float next) {
        if (!Float.isFinite(next)) return current;
        return Float.isFinite(current) ? Math.min(current, next) : next;
    }
}
