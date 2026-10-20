package com.hellovoid.liquiddock;

/**
 * Workstation taskbar geometry in physical screen coordinates.
 *
 * The native Dock is kept as the icon/layout owner. Only its GPU output is extended to cover
 * the lower screen edge and both sides; the body is deliberately oversized so its left/right/
 * bottom Prismal rim, highlight, rounded corners and shadow fall outside the visible screen.
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

    /** Visible taskbar retains the native Dock body height; optical bleed is outside the screen. */
    /**
     * Dock icon and glass may live in separate windows. Their root identities do not matter;
     * only live attachment to the same physical display and a valid body height matter.
     */
    static boolean canAlignIconRow(boolean taskbarMode, boolean workstationActive,
                                   boolean materialAttached, boolean rowAttached,
                                   int materialDisplayId, int rowDisplayId,
                                   int screenHeight, int dockHeight) {
        return taskbarMode && workstationActive && materialAttached && rowAttached
                && materialDisplayId == rowDisplayId
                && dockHeight > 0 && screenHeight > dockHeight;
    }

    static float visibleTop(int screenHeight, int nativeDockHeight) {
        return screenHeight - nativeDockHeight;
    }

    static float visibleCenter(int screenHeight, int nativeDockHeight) {
        return visibleTop(screenHeight, nativeDockHeight) + nativeDockHeight * 0.5f;
    }

    static float iconCenterCorrection(float taskbarCenter, float iconCenter) {
        return taskbarCenter - iconCenter;
    }

    static float bottomTranslation(int screenHeight, int dockScreenBottom, float translationY) {
        return translationY + (screenHeight - dockScreenBottom);
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
