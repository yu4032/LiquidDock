package com.hellovoid.liquiddock;

/**
 * Workstation taskbar glass geometry in physical screen coordinates.
 *
 * The Launcher owns the native Dock's vertical placement and animation. The glass output
 * extends to the display's sides while its body follows the moving Dock, including optical
 * bleed beyond the native body edges.
 */
final class WorkstationTaskbarGeometry {
    private WorkstationTaskbarGeometry() {}

    static int bleedPx(float density) {
        return Math.max(24, Math.round(40f * Math.max(1f, density)));
    }

    static int leftOutputMargin(int dockX, int bleed, int normalMargin) {
        return Math.max(normalMargin, Math.max(0, dockX + bleed));
    }

    static int rightOutputMargin(int dockX, int dockWidth, int screenWidth,
                                 int bleed, int normalMargin) {
        return Math.max(normalMargin,
                Math.max(0, screenWidth - dockX - dockWidth + bleed));
    }

    static int bottomOutputMargin(int dockY, int dockHeight, int screenHeight,
                                  int bleed, int normalMargin) {
        return Math.max(normalMargin,
                Math.max(0, screenHeight - dockY - dockHeight + bleed));
    }

    static float centeredIconRowTranslation(float current, float targetScreenCenter,
            float observedScreenCenter, float parentScaleY, float offsetPx) {
        if (!Float.isFinite(targetScreenCenter) || !Float.isFinite(observedScreenCenter)
                || !Float.isFinite(parentScaleY) || parentScaleY <= 0.001f) return current;
        return current + (targetScreenCenter - observedScreenCenter) / parentScaleY + offsetPx;
    }

    /** The glass body follows the currently animated native Dock bottom, not the screen edge. */
    static float movingBodyTop(float dockScreenBottom, int dockHeight) {
        return dockScreenBottom - dockHeight;
    }

    static float movingBodyCenter(float dockScreenBottom, int dockHeight) {
        return dockScreenBottom - dockHeight * .5f;
    }

    /** In motion, preserve native Y and add only our last stable optical correction. */
    static float animatedIconRowTranslation(float nativeTranslation, float stableCorrection,
                                           int newOffset, int previousOffset) {
        return nativeTranslation + stableCorrection + (newOffset - previousOffset);
    }

    static float bodyUvLeft(int sampleInsetLeft, int screenLeft, int outputScreenLeft,
                            int sampleWidth) {
        return (sampleInsetLeft + screenLeft - outputScreenLeft) / (float) sampleWidth;
    }

    static float bodyUvBottom(int sampleInsetBottom, int outputScreenBottom,
                              int screenBottom, int sampleHeight) {
        return (sampleInsetBottom + outputScreenBottom - screenBottom)
                / (float) sampleHeight;
    }
}
