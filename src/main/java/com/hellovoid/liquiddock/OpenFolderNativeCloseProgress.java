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
