package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable root-local geometry and output crop for the Gboard floating keyboard body. */
final class GboardFloatingGlassGeometry {
    final int rootWidth;
    final int rootHeight;
    final float left;
    final float top;
    final float width;
    final float height;
    final float centerX;
    final float centerY;
    final float cornerRadius;

    private GboardFloatingGlassGeometry(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadius) {
        this.rootWidth = rootWidth;
        this.rootHeight = rootHeight;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        centerX = left + width * 0.5f;
        centerY = top + height * 0.5f;
        this.cornerRadius = Math.max(0f,
                Math.min(cornerRadius, Math.min(width, height) * 0.5f));
    }

    static GboardFloatingGlassGeometry capture(View root, View keyboardArea, float cornerRadiusPx) {
        if (root == null || keyboardArea == null
                || !root.isAttachedToWindow() || !keyboardArea.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0
                || keyboardArea.getWidth() <= 0 || keyboardArea.getHeight() <= 0
                || !finite(cornerRadiusPx) || cornerRadiusPx <= 0f) return null;
        try {
            Matrix areaToGlobal = new Matrix();
            keyboardArea.transformMatrixToGlobal(areaToGlobal);
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;
            float[] points = new float[]{
                    0f, 0f,
                    keyboardArea.getWidth(), 0f,
                    keyboardArea.getWidth(), keyboardArea.getHeight(),
                    0f, keyboardArea.getHeight()
            };
            areaToGlobal.mapPoints(points);
            globalToRoot.mapPoints(points);
            float left = min(points[0], points[2], points[4], points[6]);
            float top = min(points[1], points[3], points[5], points[7]);
            float right = max(points[0], points[2], points[4], points[6]);
            float bottom = max(points[1], points[3], points[5], points[7]);
            if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)) return null;
            left = clamp(left, 0f, root.getWidth());
            top = clamp(top, 0f, root.getHeight());
            right = clamp(right, 0f, root.getWidth());
            bottom = clamp(bottom, 0f, root.getHeight());
            if (right <= left || bottom <= top) return null;
            float horizontalScale = distance(points[0], points[1], points[2], points[3])
                    / Math.max(1f, keyboardArea.getWidth());
            float verticalScale = distance(points[0], points[1], points[6], points[7])
                    / Math.max(1f, keyboardArea.getHeight());
            float visualScale = Math.min(horizontalScale, verticalScale);
            if (!finite(visualScale) || visualScale <= 0f) return null;
            return new GboardFloatingGlassGeometry(
                    root.getWidth(), root.getHeight(),
                    left, top, right - left, bottom - top,
                    cornerRadiusPx * visualScale);
        } catch (Throwable ignored) {
            return null;
        }
    }

    int outputWidthPx() {
        return Math.max(1, (int) Math.ceil(width));
    }

    int outputHeightPx() {
        return Math.max(1, (int) Math.ceil(height));
    }

    PrismalGeometry toPrismalGeometry() {
        return new PrismalGeometry(
                rootWidth, rootHeight,
                centerX, centerY,
                width, height,
                cornerRadius);
    }

    float[] toCropUvRect() {
        float cropLeft = left / rootWidth;
        float cropBottom = (rootHeight - (top + height)) / rootHeight;
        return new float[]{
                clamp(cropLeft, 0f, 1f),
                clamp(cropBottom, 0f, 1f),
                clamp(width / rootWidth, 0f, 1f),
                clamp(height / rootHeight, 0f, 1f)
        };
    }

    boolean sameAs(GboardFloatingGlassGeometry other) {
        return other != null
                && rootWidth == other.rootWidth
                && rootHeight == other.rootHeight
                && close(left, other.left)
                && close(top, other.top)
                && close(width, other.width)
                && close(height, other.height)
                && close(cornerRadius, other.cornerRadius);
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.25f;
    }
}
