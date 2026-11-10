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

    static boolean eligible(int spanX, int spanY) {
        return spanX == 1 && spanY == 1;
    }

    static int toggleSpan(int spanX, int spanY, boolean expanded) {
        if (eligible(spanX, spanY)) return 2;
        return expanded && spanX == 2 && spanY == 2 ? 1 : 0;
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

    /** Keeps the provider's layout viewport separate from the enlarged host. */
    static final class ContentViewport {
        final double logicalWidth, logicalHeight;
        final float scale, left, top;

        ContentViewport(double logicalWidth, double logicalHeight, float scale,
                        float left, float top) {
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.scale = scale;
            this.left = left;
            this.top = top;
        }
    }

    static ContentViewport contentViewport(int targetWidth, int targetHeight,
                                           int sourceWidth, int sourceHeight, float rootScale) {
        if (targetWidth <= 0 || targetHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0
                || !Float.isFinite(rootScale) || rootScale <= 0f) return null;
        float fit = Math.min((float) targetWidth / sourceWidth,
                (float) targetHeight / sourceHeight);
        return new ContentViewport((double) sourceWidth / rootScale,
                (double) sourceHeight / rootScale, fit,
                (targetWidth - sourceWidth * fit) * 0.5f,
                (targetHeight - sourceHeight * fit) * 0.5f);
    }
}
