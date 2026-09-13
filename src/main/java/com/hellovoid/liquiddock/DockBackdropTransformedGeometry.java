package com.hellovoid.liquiddock;

/** Pure geometry for converting a transformed Dock quad into screen-space sampling bounds. */
final class DockBackdropTransformedGeometry {
    static final class SampleRect {
        final float left;
        final float top;
        final float width;
        final float height;

        SampleRect(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
        }
    }

    static final class Bounds {
        final float left;
        final float top;
        final float width;
        final float height;
        final float scaleX;
        final float scaleY;

        Bounds(float left, float top, float width, float height, float scaleX, float scaleY) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
            this.scaleX = Math.max(0f, scaleX);
            this.scaleY = Math.max(0f, scaleY);
        }

        SampleRect expandLocalInsets(int leftInset, int rightInset, int topInset, int bottomInset) {
            float leftPx = Math.max(0, leftInset) * scaleX;
            float rightPx = Math.max(0, rightInset) * scaleX;
            float topPx = Math.max(0, topInset) * scaleY;
            float bottomPx = Math.max(0, bottomInset) * scaleY;
            return new SampleRect(
                    left - leftPx,
                    top - topPx,
                    width + leftPx + rightPx,
                    height + topPx + bottomPx);
        }
    }

    private DockBackdropTransformedGeometry() {}

    static Bounds fromQuad(
            float x0, float y0,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            int localWidth, int localHeight) {
        float left = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        float right = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        float top = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        float bottom = Math.max(Math.max(y0, y1), Math.max(y2, y3));
        float width = Math.max(0f, right - left);
        float height = Math.max(0f, bottom - top);
        float safeLocalWidth = Math.max(1, localWidth);
        float safeLocalHeight = Math.max(1, localHeight);
        return new Bounds(
                left, top, width, height,
                width / safeLocalWidth,
                height / safeLocalHeight);
    }
}
