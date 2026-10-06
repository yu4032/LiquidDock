package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable root-local geometry and host-local sink bounds for Gboard glass targets. */
final class GboardFloatingGlassGeometry {
    static final class CaptureContext {
        final View root;
        final View sinkHost;
        final Matrix globalToRoot;
        final Matrix globalToHost;
        final int rootWidth;
        final int rootHeight;

        CaptureContext(
                View root,
                View sinkHost,
                Matrix globalToRoot,
                Matrix globalToHost,
                int rootWidth,
                int rootHeight) {
            this.root = root;
            this.sinkHost = sinkHost;
            this.globalToRoot = globalToRoot;
            this.globalToHost = globalToHost;
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
        }
    }

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

    static CaptureContext beginCapture(View root, View sinkHost) {
        if (root == null || sinkHost == null
                || !root.isAttachedToWindow() || !sinkHost.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        try {
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;

            Matrix hostToGlobal = new Matrix();
            sinkHost.transformMatrixToGlobal(hostToGlobal);
            Matrix globalToHost = new Matrix();
            if (!hostToGlobal.invert(globalToHost)) return null;

            return new CaptureContext(
                    root,
                    sinkHost,
                    globalToRoot,
                    globalToHost,
                    root.getWidth(),
                    root.getHeight());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static GboardFloatingGlassGeometry capture(
            View root,
            View sinkHost,
            GboardFloatingStructureResolver.Structure structure,
            float cornerRadiusPx) {
        CaptureContext context = beginCapture(root, sinkHost);
        return capture(context, structure, cornerRadiusPx);
    }

    static GboardFloatingGlassGeometry capture(
            CaptureContext context,
            GboardFloatingStructureResolver.Structure structure,
            float cornerRadiusPx) {
        if (context == null || structure == null || structure.stockBackground == null
                || !finite(cornerRadiusPx) || cornerRadiusPx <= 0f) return null;
        try {
            Bounds rootShell = mapBounds(structure.stockBackground, context.globalToRoot);
            Bounds hostShell = mapBounds(structure.stockBackground, context.globalToHost);
            if (rootShell == null || hostShell == null) return null;

            Bounds rootVertical = new Bounds();
            Bounds hostVertical = new Bounds();
            addVerticalAuthority(rootVertical, structure.topEdge, context.globalToRoot);
            addVerticalAuthority(hostVertical, structure.topEdge, context.globalToHost);
            for (View holder : structure.keyboardViewHolders) {
                addVerticalAuthority(rootVertical, holder, context.globalToRoot);
                addVerticalAuthority(hostVertical, holder, context.globalToHost);
            }
            addVerticalAuthority(rootVertical, structure.bottomFrame, context.globalToRoot);
            addVerticalAuthority(hostVertical, structure.bottomFrame, context.globalToHost);
            if (!rootVertical.valid || !hostVertical.valid) return null;

            float left = clamp(rootShell.left, 0f, context.rootWidth);
            float right = clamp(rootShell.right, 0f, context.rootWidth);
            float top = clamp(rootVertical.top, 0f, context.rootHeight);
            float bottom = clamp(rootVertical.bottom, 0f, context.rootHeight);
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

            return new GboardFloatingGlassGeometry(
                    context.rootWidth, context.rootHeight,
                    left, top, right - left, bottom - top,
                    cornerRadiusPx * horizontalScale,
                    sinkLeft, sinkTop, sinkRight - sinkLeft, sinkBottom - sinkTop);
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
        if (target == null) return null;
        CaptureContext context = beginCapture(root, sinkHost);
        return captureTargetRectPadded(
                context,
                target,
                new RectF(0f, 0f, target.getWidth(), target.getHeight()),
                cornerRadiusPx,
                outputPaddingPx);
    }

    static GboardFloatingGlassGeometry captureTargetRect(
            View root,
            View sinkHost,
            View target,
            RectF localRect,
            float cornerRadiusPx) {
        CaptureContext context = beginCapture(root, sinkHost);
        return captureTargetRect(context, target, localRect, cornerRadiusPx);
    }

    static GboardFloatingGlassGeometry captureTargetRect(
            CaptureContext context,
            View target,
            RectF localRect,
            float cornerRadiusPx) {
        return captureTargetRectPadded(context, target, localRect, cornerRadiusPx, 0f);
    }

    private static GboardFloatingGlassGeometry captureTargetRectPadded(
            CaptureContext context,
            View target,
            RectF localRect,
            float cornerRadiusPx,
            float outputPaddingPx) {
        if (context == null || target == null || localRect == null
                || !target.isAttachedToWindow()
                || target.getWidth() <= 0 || target.getHeight() <= 0
                || !finite(localRect.left) || !finite(localRect.top)
                || !finite(localRect.right) || !finite(localRect.bottom)
                || localRect.width() <= 0f || localRect.height() <= 0f
                || !finite(cornerRadiusPx) || cornerRadiusPx < 0f
                || !finite(outputPaddingPx) || outputPaddingPx < 0f) return null;
        try {
            Bounds rootBounds = mapBounds(target, context.globalToRoot, localRect);
            Bounds hostBounds = mapBounds(target, context.globalToHost, localRect);
            if (rootBounds == null || hostBounds == null) return null;

            float left = clamp(rootBounds.left, 0f, context.rootWidth);
            float right = clamp(rootBounds.right, 0f, context.rootWidth);
            float top = clamp(rootBounds.top, 0f, context.rootHeight);
            float bottom = clamp(rootBounds.bottom, 0f, context.rootHeight);
            if (right <= left || bottom <= top) return null;

            float rootWidthScale = (rootBounds.right - rootBounds.left)
                    / Math.max(1f, localRect.width());
            float rootHeightScale = (rootBounds.bottom - rootBounds.top)
                    / Math.max(1f, localRect.height());
            float targetScale = Math.min(rootWidthScale, rootHeightScale);
            if (!finite(targetScale) || targetScale <= 0f) return null;

            float rootSpanX = rootBounds.right - rootBounds.left;
            float rootSpanY = rootBounds.bottom - rootBounds.top;
            float hostSpanX = hostBounds.right - hostBounds.left;
            float hostSpanY = hostBounds.bottom - hostBounds.top;
            if (rootSpanX <= 0f || rootSpanY <= 0f || hostSpanX <= 0f || hostSpanY <= 0f) {
                return null;
            }

            float cropLeft = clamp(left - outputPaddingPx, 0f, context.rootWidth);
            float cropTop = clamp(top - outputPaddingPx, 0f, context.rootHeight);
            float cropRight = clamp(right + outputPaddingPx, 0f, context.rootWidth);
            float cropBottom = clamp(bottom + outputPaddingPx, 0f, context.rootHeight);
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
                    context.rootWidth, context.rootHeight,
                    left, top, right - left, bottom - top,
                    cornerRadiusPx * targetScale,
                    sinkLeft, sinkTop, sinkRight - sinkLeft, sinkBottom - sinkTop,
                    cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop);
        } catch (Throwable ignored) {
            return null;
        }
    }

    GboardFloatingGlassGeometry translated(float dx, float dy) {
        if (!finite(dx) || !finite(dy) || (close(dx, 0f) && close(dy, 0f))) return this;
        return new GboardFloatingGlassGeometry(
                rootWidth,
                rootHeight,
                left + dx,
                top + dy,
                width,
                height,
                cornerRadius,
                sinkLeft,
                sinkTop,
                sinkWidth,
                sinkHeight,
                cropLeft + dx,
                cropTop + dy,
                cropWidth,
                cropHeight);
    }

    int sinkWidthPx() {
        return Math.max(1, (int) Math.ceil(sinkWidth));
    }

    int sinkHeightPx() {
        return Math.max(1, (int) Math.ceil(sinkHeight));
    }

    PrismalGeometry toPrismalGeometry() {
        return toPrismalGeometry(0f, 0f);
    }

    PrismalGeometry toPrismalGeometry(float dx, float dy) {
        return new PrismalGeometry(
                rootWidth, rootHeight,
                centerX + dx, centerY + dy,
                width, height,
                cornerRadius);
    }

    float[] toCropUvRect() {
        return toCropUvRect(0f, 0f);
    }

    float[] toCropUvRect(float dx, float dy) {
        float projectedCropLeft = cropLeft + dx;
        float projectedCropTop = cropTop + dy;
        float cropUvLeft = projectedCropLeft / rootWidth;
        float cropBottom = (rootHeight - (projectedCropTop + cropHeight)) / rootHeight;
        return new float[]{
                clamp(cropUvLeft, 0f, 1f),
                clamp(cropBottom, 0f, 1f),
                clamp(cropWidth / rootWidth, 0f, 1f),
                clamp(cropHeight / rootHeight, 0f, 1f)
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
        if (view == null) return null;
        return mapBounds(
                view,
                globalToTarget,
                new RectF(0f, 0f, view.getWidth(), view.getHeight()));
    }

    private static Bounds mapBounds(
            View view, Matrix globalToTarget, RectF localRect) {
        if (view == null || globalToTarget == null || localRect == null
                || !view.isAttachedToWindow()
                || view.getWidth() <= 0 || view.getHeight() <= 0
                || localRect.width() <= 0f || localRect.height() <= 0f) return null;
        Matrix viewToGlobal = new Matrix();
        view.transformMatrixToGlobal(viewToGlobal);
        float[] points = new float[]{
                localRect.left, localRect.top,
                localRect.right, localRect.top,
                localRect.right, localRect.bottom,
                localRect.left, localRect.bottom
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
