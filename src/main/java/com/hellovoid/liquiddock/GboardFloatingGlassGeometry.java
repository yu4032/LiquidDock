package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable root-local geometry and host-local sink bounds for Gboard glass targets. */
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
    final float sinkLeft;
    final float sinkTop;
    private final float sinkWidth;
    private final float sinkHeight;
    private final float cropLeft;
    private final float cropTop;
    private final float cropWidth;
    private final float cropHeight;

    private GboardFloatingGlassGeometry(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadius,
            float sinkLeft,
            float sinkTop,
            float sinkWidth,
            float sinkHeight) {
        this(rootWidth, rootHeight,
                left, top, width, height, cornerRadius,
                sinkLeft, sinkTop, sinkWidth, sinkHeight,
                left, top, width, height);
    }

    private GboardFloatingGlassGeometry(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadius,
            float sinkLeft,
            float sinkTop,
            float sinkWidth,
            float sinkHeight,
            float cropLeft,
            float cropTop,
            float cropWidth,
            float cropHeight) {
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
        this.sinkLeft = sinkLeft;
        this.sinkTop = sinkTop;
        this.sinkWidth = sinkWidth;
        this.sinkHeight = sinkHeight;
        this.cropLeft = cropLeft;
        this.cropTop = cropTop;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
    }

    static GboardFloatingGlassGeometry capture(
            View root,
            View sinkHost,
            GboardFloatingStructureResolver.Structure structure,
            float cornerRadiusPx) {
        if (root == null || sinkHost == null || structure == null
                || structure.stockBackground == null
                || !root.isAttachedToWindow() || !sinkHost.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0
                || !finite(cornerRadiusPx) || cornerRadiusPx <= 0f) return null;
        try {
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            Matrix hostToGlobal = new Matrix();
            sinkHost.transformMatrixToGlobal(hostToGlobal);
            Matrix globalToHost = new Matrix();
            if (!hostToGlobal.invert(globalToHost)) return null;

            Bounds rootShell = mapBounds(structure.stockBackground, globalToRoot);
            Bounds hostShell = mapBounds(structure.stockBackground, globalToHost);
            if (rootShell == null || hostShell == null) return null;

            Bounds rootVertical = new Bounds();
            Bounds hostVertical = new Bounds();
            addVerticalAuthority(rootVertical, structure.topEdge, globalToRoot);
            addVerticalAuthority(hostVertical, structure.topEdge, globalToHost);
            for (View holder : structure.keyboardViewHolders) {
                addVerticalAuthority(rootVertical, holder, globalToRoot);
                addVerticalAuthority(hostVertical, holder, globalToHost);
            }
            addVerticalAuthority(rootVertical, structure.bottomFrame, globalToRoot);
            addVerticalAuthority(hostVertical, structure.bottomFrame, globalToHost);
            if (!rootVertical.valid || !hostVertical.valid) return null;

            float left = clamp(rootShell.left, 0f, root.getWidth());
            float right = clamp(rootShell.right, 0f, root.getWidth());
            float top = clamp(rootVertical.top, 0f, root.getHeight());
            float bottom = clamp(rootVertical.bottom, 0f, root.getHeight());
            if (right <= left || bottom <= top) return null;

            float sinkLeft = hostShell.left;
            float sinkRight = hostShell.right;
            float sinkTop = hostVertical.top;
            float sinkBottom = hostVertical.bottom;
            if (!finite(sinkLeft) || !finite(sinkRight)
                    || !finite(sinkTop) || !finite(sinkBottom)
                    || sinkRight <= sinkLeft || sinkBottom <= sinkTop) return null;

            float horizontalScale = (rootShell.right - rootShell.left)
                    / Math.max(1f, structure.stockBackground.getWidth());
            if (!finite(horizontalScale) || horizontalScale <= 0f) return null;

            GboardFloatingGlassGeometry geometry = new GboardFloatingGlassGeometry(
                    root.getWidth(), root.getHeight(),
                    left, top, right - left, bottom - top,
                    cornerRadiusPx * horizontalScale,
                    sinkLeft, sinkTop, sinkRight - sinkLeft, sinkBottom - sinkTop);
            GboardDragDiagnostics.geometryCaptured(geometry);
            return geometry;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static GboardFloatingGlassGeometry captureTarget(
            View root,
            View sinkHost,
            View target,
            float cornerRadiusPx) {
        return captureTargetPadded(root, sinkHost, target, cornerRadiusPx, 0f);
    }

    static GboardFloatingGlassGeometry captureTargetPadded(
            View root,
            View sinkHost,
            View target,
            float cornerRadiusPx,
            float outputPaddingPx) {
        if (root == null || sinkHost == null || target == null
                || !root.isAttachedToWindow() || !sinkHost.isAttachedToWindow()
                || !target.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0
                || !finite(cornerRadiusPx) || cornerRadiusPx <= 0f
                || !finite(outputPaddingPx) || outputPaddingPx < 0f) return null;
        try {
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            Matrix hostToGlobal = new Matrix();
            sinkHost.transformMatrixToGlobal(hostToGlobal);
            Matrix globalToHost = new Matrix();
            if (!hostToGlobal.invert(globalToHost)) return null;

            Bounds rootBounds = mapBounds(target, globalToRoot);
            Bounds hostBounds = mapBounds(target, globalToHost);
            if (rootBounds == null || hostBounds == null) return null;

            float left = clamp(rootBounds.left, 0f, root.getWidth());
            float right = clamp(rootBounds.right, 0f, root.getWidth());
            float top = clamp(rootBounds.top, 0f, root.getHeight());
            float bottom = clamp(rootBounds.bottom, 0f, root.getHeight());
            if (right <= left || bottom <= top) return null;

            float rootWidthScale = (rootBounds.right - rootBounds.left)
                    / Math.max(1f, target.getWidth());
            float rootHeightScale = (rootBounds.bottom - rootBounds.top)
                    / Math.max(1f, target.getHeight());
            float targetScale = Math.min(rootWidthScale, rootHeightScale);
            if (!finite(targetScale) || targetScale <= 0f) return null;

            float rootSpanX = rootBounds.right - rootBounds.left;
            float rootSpanY = rootBounds.bottom - rootBounds.top;
            float hostSpanX = hostBounds.right - hostBounds.left;
            float hostSpanY = hostBounds.bottom - hostBounds.top;
            if (rootSpanX <= 0f || rootSpanY <= 0f || hostSpanX <= 0f || hostSpanY <= 0f) {
                return null;
            }

            float cropLeft = clamp(left - outputPaddingPx, 0f, root.getWidth());
            float cropTop = clamp(top - outputPaddingPx, 0f, root.getHeight());
            float cropRight = clamp(right + outputPaddingPx, 0f, root.getWidth());
            float cropBottom = clamp(bottom + outputPaddingPx, 0f, root.getHeight());
            if (cropRight <= cropLeft || cropBottom <= cropTop) return null;

            float hostPaddingX = outputPaddingPx * hostSpanX / rootSpanX;
            float hostPaddingY = outputPaddingPx * hostSpanY / rootSpanY;
            float sinkLeft = hostBounds.left - hostPaddingX;
            float sinkTop = hostBounds.top - hostPaddingY;
            float sinkRight = hostBounds.right + hostPaddingX;
            float sinkBottom = hostBounds.bottom + hostPaddingY;
            if (!finite(sinkLeft) || !finite(sinkTop)
                    || !finite(sinkRight) || !finite(sinkBottom)
                    || sinkRight <= sinkLeft || sinkBottom <= sinkTop) return null;

            return new GboardFloatingGlassGeometry(
                    root.getWidth(), root.getHeight(),
                    left, top, right - left, bottom - top,
                    cornerRadiusPx * targetScale,
                    sinkLeft, sinkTop, sinkRight - sinkLeft, sinkBottom - sinkTop,
                    cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop);
        } catch (Throwable ignored) {
            return null;
        }
    }

    int sinkWidthPx() {
        return Math.max(1, (int) Math.ceil(sinkWidth));
    }

    int sinkHeightPx() {
        return Math.max(1, (int) Math.ceil(sinkHeight));
    }

    PrismalGeometry toPrismalGeometry() {
        return new PrismalGeometry(
                rootWidth, rootHeight,
                centerX, centerY,
                width, height,
                cornerRadius);
    }

    float[] toCropUvRect() {
        float cropUvLeft = cropLeft / rootWidth;
        float cropBottom = (rootHeight - (cropTop + cropHeight)) / rootHeight;
        float[] crop = new float[]{
                clamp(cropUvLeft, 0f, 1f),
                clamp(cropBottom, 0f, 1f),
                clamp(cropWidth / rootWidth, 0f, 1f),
                clamp(cropHeight / rootHeight, 0f, 1f)
        };
        GboardDragDiagnostics.renderCrop(this, crop);
        return crop;
    }

    boolean sameAs(GboardFloatingGlassGeometry other) {
        return other != null
                && rootWidth == other.rootWidth
                && rootHeight == other.rootHeight
                && close(left, other.left)
                && close(top, other.top)
                && close(width, other.width)
                && close(height, other.height)
                && close(cornerRadius, other.cornerRadius)
                && close(sinkLeft, other.sinkLeft)
                && close(sinkTop, other.sinkTop)
                && close(sinkWidth, other.sinkWidth)
                && close(sinkHeight, other.sinkHeight)
                && close(cropLeft, other.cropLeft)
                && close(cropTop, other.cropTop)
                && close(cropWidth, other.cropWidth)
                && close(cropHeight, other.cropHeight);
    }

    private static void addVerticalAuthority(Bounds target, View view, Matrix globalToTarget) {
        Bounds bounds = mapBounds(view, globalToTarget);
        if (bounds == null) return;
        target.includeVertical(bounds.top, bounds.bottom);
    }

    private static Bounds mapBounds(View view, Matrix globalToTarget) {
        if (view == null || globalToTarget == null || !view.isAttachedToWindow()
                || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Matrix viewToGlobal = new Matrix();
        view.transformMatrixToGlobal(viewToGlobal);
        float[] points = new float[]{
                0f, 0f,
                view.getWidth(), 0f,
                view.getWidth(), view.getHeight(),
                0f, view.getHeight()
        };
        viewToGlobal.mapPoints(points);
        globalToTarget.mapPoints(points);
        float left = min(points[0], points[2], points[4], points[6]);
        float top = min(points[1], points[3], points[5], points[7]);
        float right = max(points[0], points[2], points[4], points[6]);
        float bottom = max(points[1], points[3], points[5], points[7]);
        if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                || right <= left || bottom <= top) return null;
        return new Bounds(left, top, right, bottom);
    }

    private static final class Bounds {
        float left;
        float top;
        float right;
        float bottom;
        boolean valid;

        Bounds() {}

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            valid = true;
        }

        void includeVertical(float nextTop, float nextBottom) {
            if (!finite(nextTop) || !finite(nextBottom) || nextBottom <= nextTop) return;
            if (!valid) {
                top = nextTop;
                bottom = nextBottom;
                valid = true;
                return;
            }
            top = Math.min(top, nextTop);
            bottom = Math.max(bottom, nextBottom);
        }
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
