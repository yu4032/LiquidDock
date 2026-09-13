package com.hellovoid.liquiddock;

/** Pure geometry for mapping a Dock-local material rectangle into the PassBlur producer window. */
final class Miuix307BackdropMapping {
    enum Coverage {
        FULL,
        PARTIAL,
        OUTSIDE
    }

    static final class Result {
        final float backdropX;
        final float backdropY;
        final float backdropW;
        final float backdropH;
        final float validLeft;
        final float validBottom;
        final float validRight;
        final float validTop;
        final Coverage coverage;

        Result(float backdropX, float backdropY, float backdropW, float backdropH,
               float validLeft, float validBottom, float validRight, float validTop,
               Coverage coverage) {
            this.backdropX = backdropX;
            this.backdropY = backdropY;
            this.backdropW = backdropW;
            this.backdropH = backdropH;
            this.validLeft = validLeft;
            this.validBottom = validBottom;
            this.validRight = validRight;
            this.validTop = validTop;
            this.coverage = coverage;
        }
    }

    private Miuix307BackdropMapping() {}

    static Result compute(
            float hostLeft, float hostTop, float hostWidth, float hostHeight,
            float frameLeft, float frameTop, float frameWidth, float frameHeight) {
        if (hostWidth <= 0f || hostHeight <= 0f || frameWidth <= 0f || frameHeight <= 0f) {
            return outside();
        }

        float backdropX = (hostLeft - frameLeft) / frameWidth;
        float top = (hostTop - frameTop) / frameHeight;
        float backdropW = hostWidth / frameWidth;
        float backdropH = hostHeight / frameHeight;
        float backdropY = 1f - (top + backdropH);

        float hostRight = hostLeft + hostWidth;
        float hostBottom = hostTop + hostHeight;
        float frameRight = frameLeft + frameWidth;
        float frameBottom = frameTop + frameHeight;

        float intersectionLeft = Math.max(hostLeft, frameLeft);
        float intersectionTop = Math.max(hostTop, frameTop);
        float intersectionRight = Math.min(hostRight, frameRight);
        float intersectionBottom = Math.min(hostBottom, frameBottom);

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return new Result(
                    backdropX, backdropY, backdropW, backdropH,
                    0f, 0f, 0f, 0f, Coverage.OUTSIDE);
        }

        float validLeft = clamp01((intersectionLeft - hostLeft) / hostWidth);
        float validRight = clamp01((intersectionRight - hostLeft) / hostWidth);

        // Android screen coordinates are top-left based while the Dock-local shader UV is
        // bottom-left based. Convert the visible top-down host interval into GL UV coordinates.
        float validBottom = clamp01(1f - (intersectionBottom - hostTop) / hostHeight);
        float validTop = clamp01(1f - (intersectionTop - hostTop) / hostHeight);

        boolean full = approximatelyEqual(intersectionLeft, hostLeft)
                && approximatelyEqual(intersectionTop, hostTop)
                && approximatelyEqual(intersectionRight, hostRight)
                && approximatelyEqual(intersectionBottom, hostBottom);
        return new Result(
                backdropX, backdropY, backdropW, backdropH,
                validLeft, validBottom, validRight, validTop,
                full ? Coverage.FULL : Coverage.PARTIAL);
    }

    private static boolean approximatelyEqual(float a, float b) {
        return Math.abs(a - b) <= 0.0001f;
    }

    private static Result outside() {
        return new Result(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, Coverage.OUTSIDE);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
