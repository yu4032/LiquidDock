package com.hellovoid.liquiddock;

/** Pure visual-bounds transform matching Android View pivot/scale/translation properties. */
final class SecurityCenterTransformedBounds {
    final float left;
    final float top;
    final float right;
    final float bottom;

    private SecurityCenterTransformedBounds(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    static SecurityCenterTransformedBounds resolve(
            float left,
            float top,
            float width,
            float height,
            float pivotX,
            float pivotY,
            float scaleX,
            float scaleY,
            float translationX,
            float translationY) {
        if (!finite(left) || !finite(top) || !finite(width) || !finite(height)
                || !finite(pivotX) || !finite(pivotY)
                || !finite(scaleX) || !finite(scaleY)
                || !finite(translationX) || !finite(translationY)
                || width <= 0f || height <= 0f || scaleX <= 0f || scaleY <= 0f) {
            return null;
        }
        float visualLeft = left + pivotX * (1f - scaleX) + translationX;
        float visualTop = top + pivotY * (1f - scaleY) + translationY;
        float visualRight = visualLeft + width * scaleX;
        float visualBottom = visualTop + height * scaleY;
        if (!finite(visualLeft) || !finite(visualTop)
                || !finite(visualRight) || !finite(visualBottom)
                || visualRight <= visualLeft || visualBottom <= visualTop) {
            return null;
        }
        return new SecurityCenterTransformedBounds(
                visualLeft, visualTop, visualRight, visualBottom);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
