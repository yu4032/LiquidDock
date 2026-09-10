package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable root-local geometry for one Security Center large glass surface. */
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

    private SecurityCenterGlassGeometry(
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

    PrismalGeometry toPrismalGeometry() {
        return new PrismalGeometry(
                rootWidth, rootHeight,
                centerX, centerY,
                width, height,
                cornerRadius);
    }

    boolean sameAs(SecurityCenterGlassGeometry other) {
        return other != null
                && rootWidth == other.rootWidth
                && rootHeight == other.rootHeight
                && close(left, other.left)
                && close(top, other.top)
                && close(width, other.width)
                && close(height, other.height)
                && close(cornerRadius, other.cornerRadius);
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
