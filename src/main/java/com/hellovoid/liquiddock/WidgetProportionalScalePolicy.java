package com.hellovoid.liquiddock;

/** Geometry only; Launcher remains responsible for cell occupancy and persistence. */
final class WidgetProportionalScalePolicy {
    private WidgetProportionalScalePolicy() {}

    static final class Frame {
        final int left, top, width, height;
        final float scale;
        Frame(int left, int top, int width, int height, float scale) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.scale = scale;
        }
    }

    static boolean eligible(int spanX, int spanY, int resizeMode) {
        return spanX == 1 && spanY == 1 && resizeMode == 0;
    }

    /** One uniform scale, centered in the native 2x2 host bounds. */
    static Frame fit(int left, int top, int targetWidth, int targetHeight,
                     int cellWidth, int cellHeight) {
        if (targetWidth <= 0 || targetHeight <= 0 || cellWidth <= 0 || cellHeight <= 0) {
            return null;
        }
        int width = Math.min(targetWidth, cellWidth);
        int height = Math.min(targetHeight, cellHeight);
        float scale = Math.min((float) targetWidth / width, (float) targetHeight / height);
        if (!Float.isFinite(scale) || scale < 1f) return null;
        return new Frame(left + (targetWidth - width) / 2,
                top + (targetHeight - height) / 2, width, height, scale);
    }
}
