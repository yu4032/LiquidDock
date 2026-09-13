package com.hellovoid.liquiddock;

/** Pure geometry for keeping an oversized Dock backdrop fixed in screen/world coordinates. */
final class DockBackdropWorldLockState {
    static final class Crop {
        final int sourceLeft;
        final int sourceTop;
        final float scaleX;
        final float scaleY;
        final float translateX;
        final float translateY;

        Crop(int sourceLeft, int sourceTop, float scaleX, float scaleY,
             float translateX, float translateY) {
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.translateX = translateX;
            this.translateY = translateY;
        }
    }

    private DockBackdropWorldLockState() {}

    static Crop compute(
            int visibleWidth, int visibleHeight,
            int sampleWidth, int sampleHeight,
            int insetLeft, int insetTop,
            int presentedScreenX, int presentedScreenY,
            int currentScreenX, int currentScreenY) {
        int safeVisibleWidth = Math.max(1, visibleWidth);
        int safeVisibleHeight = Math.max(1, visibleHeight);
        int safeSampleWidth = Math.max(safeVisibleWidth, sampleWidth);
        int safeSampleHeight = Math.max(safeVisibleHeight, sampleHeight);
        int maxLeft = Math.max(0, safeSampleWidth - safeVisibleWidth);
        int maxTop = Math.max(0, safeSampleHeight - safeVisibleHeight);
        int desiredLeft = insetLeft + (currentScreenX - presentedScreenX);
        int desiredTop = insetTop + (currentScreenY - presentedScreenY);
        int sourceLeft = clamp(desiredLeft, 0, maxLeft);
        int sourceTop = clamp(desiredTop, 0, maxTop);
        return new Crop(
                sourceLeft,
                sourceTop,
                safeSampleWidth / (float) safeVisibleWidth,
                safeSampleHeight / (float) safeVisibleHeight,
                -sourceLeft,
                -sourceTop);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
