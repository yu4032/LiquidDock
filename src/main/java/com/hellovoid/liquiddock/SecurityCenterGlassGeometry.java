package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable root-local geometry for one Security Center glass node or presentation region. */
final class SecurityCenterGlassGeometry {
    final int rootWidth;
    final int rootHeight;
    final float left;
    final float top;
    final float width;
    final float height;
    final float centerX;
    final float centerY;
    final float cornerRadius;
    private final float cropLeftPx;
    private final float cropTopPx;
    private final float cropWidthPx;
    private final float cropHeightPx;

    private SecurityCenterGlassGeometry(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadius) {
        this(rootWidth, rootHeight, left, top, width, height, cornerRadius,
                left, top, width, height);
    }

    private SecurityCenterGlassGeometry(
            int rootWidth,
            int rootHeight,
            float left,
            float top,
            float width,
            float height,
            float cornerRadius,
            float cropLeftPx,
            float cropTopPx,
            float cropWidthPx,
            float cropHeightPx) {
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
        this.cropLeftPx = cropLeftPx;
        this.cropTopPx = cropTopPx;
        this.cropWidthPx = cropWidthPx;
        this.cropHeightPx = cropHeightPx;
    }

    /** Maps a screen-space target rectangle into the authoritative root's local coordinates. */
    static SecurityCenterGlassGeometry resolve(
            int rootWidth,
            int rootHeight,
            float rootScreenLeft,
            float rootScreenTop,
            float targetScreenLeft,
            float targetScreenTop,
            float targetScreenRight,
            float targetScreenBottom,
            float cornerRadiusPx) {
        if (rootWidth <= 0 || rootHeight <= 0
                || !finite(rootScreenLeft) || !finite(rootScreenTop)
                || !finite(targetScreenLeft) || !finite(targetScreenTop)
                || !finite(targetScreenRight) || !finite(targetScreenBottom)
                || targetScreenRight <= targetScreenLeft
                || targetScreenBottom <= targetScreenTop) {
            return null;
        }

        float localLeft = targetScreenLeft - rootScreenLeft;
        float localTop = targetScreenTop - rootScreenTop;
        float localRight = targetScreenRight - rootScreenLeft;
        float localBottom = targetScreenBottom - rootScreenTop;
        if (localRight <= 0f || localBottom <= 0f
                || localLeft >= rootWidth || localTop >= rootHeight) return null;

        float left = clamp(localLeft, 0f, rootWidth);
        float top = clamp(localTop, 0f, rootHeight);
        float right = clamp(localRight, 0f, rootWidth);
        float bottom = clamp(localBottom, 0f, rootHeight);
        if (right <= left || bottom <= top) return null;
        float safeRadius = finite(cornerRadiusPx) ? Math.max(0f, cornerRadiusPx) : 0f;
        return new SecurityCenterGlassGeometry(
                rootWidth, rootHeight, left, top, right - left, bottom - top, safeRadius);
    }

    /**
     * Expands only the presentation crop. The Prismal SDF shape remains exactly unchanged so
     * outside-AA/highlight pixels have transparent room without moving the visible glass edge.
     */
    SecurityCenterGlassGeometry expandedBy(float outsetPx) {
        if (!finite(outsetPx) || outsetPx <= 0f) return this;
        float cropLeft = clamp(left - outsetPx, 0f, rootWidth);
        float cropTop = clamp(top - outsetPx, 0f, rootHeight);
        float cropRight = clamp(left + width + outsetPx, 0f, rootWidth);
        float cropBottom = clamp(top + height + outsetPx, 0f, rootHeight);
        if (cropRight <= cropLeft || cropBottom <= cropTop) return this;
        return new SecurityCenterGlassGeometry(
                rootWidth, rootHeight,
                left, top, width, height, cornerRadius,
                cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop);
    }

    /** Preserves the animated shape while presenting it from a full-root screen-space crop. */
    SecurityCenterGlassGeometry withRootCrop() {
        return new SecurityCenterGlassGeometry(
                rootWidth, rootHeight,
                left, top, width, height, cornerRadius,
                0f, 0f, rootWidth, rootHeight);
    }

    /** Exact root-local union used only as an output/crop region; node radii remain on the nodes. */
    static SecurityCenterGlassGeometry covering(
            SecurityCenterGlassGeometry first,
            SecurityCenterGlassGeometry second) {
        if (first == null || second == null
                || first.rootWidth != second.rootWidth
                || first.rootHeight != second.rootHeight) return null;
        float left = Math.min(first.left, second.left);
        float top = Math.min(first.top, second.top);
        float right = Math.max(first.left + first.width, second.left + second.width);
        float bottom = Math.max(first.top + first.height, second.top + second.height);
        return new SecurityCenterGlassGeometry(
                first.rootWidth, first.rootHeight,
                left, top, right - left, bottom - top, 0f);
    }

    PrismalGeometry toPrismalGeometry() {
        return new PrismalGeometry(
                rootWidth, rootHeight,
                centerX, centerY,
                width, height,
                cornerRadius);
    }

    /**
     * Crop rectangle for a conventional GL FBO texture. Geometry is top-left/root-local while
     * texture v=0 is the visual bottom, matching LauncherGlassSession's verified presentation.
     */
    float[] toCropUvRect() {
        float cropLeft = cropLeftPx / rootWidth;
        float cropBottom = (rootHeight - (cropTopPx + cropHeightPx)) / rootHeight;
        return new float[]{
                clamp(cropLeft, 0f, 1f),
                clamp(cropBottom, 0f, 1f),
                clamp(cropWidthPx / rootWidth, 0f, 1f),
                clamp(cropHeightPx / rootHeight, 0f, 1f)
        };
    }

    /** Maps this root-local target into the actual output parent's local screen-aligned space. */
    float[] toParentPlacement(
            float rootScreenLeft,
            float rootScreenTop,
            float parentScreenLeft,
            float parentScreenTop) {
        if (!finite(rootScreenLeft) || !finite(rootScreenTop)
                || !finite(parentScreenLeft) || !finite(parentScreenTop)) return null;
        return new float[]{
                rootScreenLeft + left - parentScreenLeft,
                rootScreenTop + top - parentScreenTop,
                width,
                height
        };
    }

    boolean sameAs(SecurityCenterGlassGeometry other) {
        return other != null
                && rootWidth == other.rootWidth
                && rootHeight == other.rootHeight
                && close(left, other.left)
                && close(top, other.top)
                && close(width, other.width)
                && close(height, other.height)
                && close(cornerRadius, other.cornerRadius)
                && close(cropLeftPx, other.cropLeftPx)
                && close(cropTopPx, other.cropTopPx)
                && close(cropWidthPx, other.cropWidthPx)
                && close(cropHeightPx, other.cropHeightPx);
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
