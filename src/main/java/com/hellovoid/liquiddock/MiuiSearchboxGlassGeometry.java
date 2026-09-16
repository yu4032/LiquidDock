package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;
import android.view.ViewParent;

import com.hellovoid.prismal.PrismalGeometry;

/** Window-local settled geometry for SearchActivityBackground and its matching PassBlur crop. */
final class MiuiSearchboxGlassGeometry {
    final int rootWidth;
    final int rootHeight;
    final float left;
    final float top;
    final float width;
    final float height;
    final float centerX;
    final float centerY;
    final float cornerRadius;

    private MiuiSearchboxGlassGeometry(
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

    /** Ignores transient ancestor translations so entrance animation never shifts the backdrop crop. */
    static MiuiSearchboxGlassGeometry capture(View windowRoot, View target, float cornerRadiusPx) {
        if (windowRoot == null || target == null
                || !windowRoot.isAttachedToWindow() || !target.isAttachedToWindow()
                || windowRoot.getWidth() <= 0 || windowRoot.getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        try {
            Matrix rootToGlobal = new Matrix();
            windowRoot.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            Matrix targetToGlobal = new Matrix();
            target.transformMatrixToGlobal(targetToGlobal);
            float[] points = new float[]{
                    0f, 0f,
                    target.getWidth(), 0f,
                    target.getWidth(), target.getHeight(),
                    0f, target.getHeight()
            };
            targetToGlobal.mapPoints(points);
            globalToRoot.mapPoints(points);

            float[] translation = cumulativeTranslation(target, windowRoot);
            for (int i = 0; i < points.length; i += 2) {
                points[i] = settledCoordinate(points[i], translation[0]);
                points[i + 1] = settledCoordinate(points[i + 1], translation[1]);
            }

            float left = min(points[0], points[2], points[4], points[6]);
            float top = min(points[1], points[3], points[5], points[7]);
            float right = max(points[0], points[2], points[4], points[6]);
            float bottom = max(points[1], points[3], points[5], points[7]);
            return fromWindowBounds(
                    windowRoot.getWidth(), windowRoot.getHeight(),
                    left, top, right - left, bottom - top,
                    cornerRadiusPx);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float[] cumulativeTranslation(View target, View windowRoot) {
        float x = 0f;
        float y = 0f;
        View current = target;
        while (current != null && current != windowRoot) {
            x += current.getTranslationX();
            y += current.getTranslationY();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return new float[]{x, y};
    }

    static float settledCoordinate(float animatedCoordinate, float cumulativeTranslation) {
        return animatedCoordinate - cumulativeTranslation;
    }

    static MiuiSearchboxGlassGeometry fromWindowBounds(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadiusPx) {
        if (rootWidth <= 0 || rootHeight <= 0
                || !finite(left) || !finite(top) || !finite(width) || !finite(height)
                || width <= 0f || height <= 0f) return null;
        float clampedLeft = clamp(left, 0f, rootWidth);
        float clampedTop = clamp(top, 0f, rootHeight);
        float clampedRight = clamp(left + width, 0f, rootWidth);
        float clampedBottom = clamp(top + height, 0f, rootHeight);
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null;
        return new MiuiSearchboxGlassGeometry(
                rootWidth,
                rootHeight,
                clampedLeft,
                clampedTop,
                clampedRight - clampedLeft,
                clampedBottom - clampedTop,
                cornerRadiusPx);
    }

    PrismalGeometry toPrismalGeometry() {
        return new PrismalGeometry(
                rootWidth,
                rootHeight,
                centerX,
                centerY,
                width,
                height,
                cornerRadius);
    }

    float[] toCropUvRect() {
        float uvLeft = left / rootWidth;
        float uvBottom = (rootHeight - (top + height)) / rootHeight;
        return new float[]{
                clamp(uvLeft, 0f, 1f),
                clamp(uvBottom, 0f, 1f),
                clamp(width / rootWidth, 0f, 1f),
                clamp(height / rootHeight, 0f, 1f)
        };
    }

    boolean sameAs(MiuiSearchboxGlassGeometry other) {
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
