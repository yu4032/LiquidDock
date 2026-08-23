package com.hellovoid.liquiddock;

/**
 * Resolves already-mapped DragView geometry into the child-layout coordinates of the sibling
 * glass carrier. The mapped points already contain the source/ancestor transforms, so callers
 * must not reapply source pivot/scale/rotation to the carrier.
 */
final class LauncherGlassDragCarrierGeometry {
    private LauncherGlassDragCarrierGeometry() {}

    static final class Snapshot {
        final float carrierLeft;
        final float carrierTop;
        final float carrierRight;
        final float carrierBottom;
        final float visualLeft;
        final float visualTop;
        final float visualRight;
        final float visualBottom;

        Snapshot(
                float carrierLeft, float carrierTop, float carrierRight, float carrierBottom,
                float visualLeft, float visualTop, float visualRight, float visualBottom) {
            this.carrierLeft = carrierLeft;
            this.carrierTop = carrierTop;
            this.carrierRight = carrierRight;
            this.carrierBottom = carrierBottom;
            this.visualLeft = visualLeft;
            this.visualTop = visualTop;
            this.visualRight = visualRight;
            this.visualBottom = visualBottom;
        }

        float carrierWidth() { return carrierRight - carrierLeft; }
        float carrierHeight() { return carrierBottom - carrierTop; }
        float visualWidth() { return visualRight - visualLeft; }
        float visualHeight() { return visualBottom - visualTop; }
        float visualCenterX() { return carrierLeft + (visualLeft + visualRight) * 0.5f; }
        float visualCenterY() { return carrierTop + (visualTop + visualBottom) * 0.5f; }
    }

    static Snapshot resolve(
            float[] mappedSourcePoints, float[] mappedVisualPoints,
            float hostScrollX, float hostScrollY) {
        Bounds source = bounds(mappedSourcePoints);
        Bounds visual = bounds(mappedVisualPoints);
        if (source == null || visual == null
                || !Float.isFinite(hostScrollX) || !Float.isFinite(hostScrollY)) {
            return null;
        }

        // Inverting host.transformMatrixToGlobal() yields host viewport coordinates. A sibling
        // child's layout coordinates live in the host's scrollable content space, so restore the
        // host scroll exactly once before assigning carrier X/Y.
        float carrierLeft = source.left + hostScrollX;
        float carrierTop = source.top + hostScrollY;
        float carrierRight = source.right + hostScrollX;
        float carrierBottom = source.bottom + hostScrollY;

        if (!(carrierRight > carrierLeft) || !(carrierBottom > carrierTop)) return null;

        // The same host-scroll restoration is present in both rectangles and therefore cancels
        // when the visual rect is expressed in carrier-local coordinates.
        float visualLeft = visual.left - source.left;
        float visualTop = visual.top - source.top;
        float visualRight = visual.right - source.left;
        float visualBottom = visual.bottom - source.top;
        if (!(visualRight > visualLeft) || !(visualBottom > visualTop)) return null;

        return new Snapshot(
                carrierLeft, carrierTop, carrierRight, carrierBottom,
                visualLeft, visualTop, visualRight, visualBottom);
    }

    private static Bounds bounds(float[] points) {
        if (points == null || points.length < 8) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i += 2) {
            float x = points[i];
            float y = points[i + 1];
            if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
        if (!(right > left) || !(bottom > top)) return null;
        return new Bounds(left, top, right, bottom);
    }

    private static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
