package com.hellovoid.liquiddock;

/** Pure geometry for mapping a Dock-local material rectangle into the PassBlur producer window. */
final class Miuix307BackdropMapping {
    enum Coverage {
        FULL,
        PARTIAL,
        OUTSIDE
    }

    static final class MutableResult {
        float backdropX;
        float backdropY;
        float backdropW;
        float backdropH;
        float validLeft;
        float validBottom;
        float validRight;
        float validTop;
        Coverage coverage = Coverage.OUTSIDE;

        void set(
                float backdropX, float backdropY, float backdropW, float backdropH,
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
            int hostLeft, int hostTop, int hostWidth, int hostHeight,
            int frameLeft, int frameTop, int frameWidth, int frameHeight) {
        MutableResult out = new MutableResult();
        computeInto(out, hostLeft, hostTop, hostWidth, hostHeight,
                frameLeft, frameTop, frameWidth, frameHeight);
        return new Result(
                out.backdropX, out.backdropY, out.backdropW, out.backdropH,
                out.validLeft, out.validBottom, out.validRight, out.validTop, out.coverage);
    }

    static void computeInto(
            MutableResult out,
            int hostLeft, int hostTop, int hostWidth, int hostHeight,
            int frameLeft, int frameTop, int frameWidth, int frameHeight) {
        if (out == null) throw new IllegalArgumentException("out == null");
        if (hostWidth <= 0 || hostHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            out.set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, Coverage.OUTSIDE);
            return;
        }

        float backdropX = (hostLeft - frameLeft) / (float) frameWidth;
        float top = (hostTop - frameTop) / (float) frameHeight;
        float backdropW = hostWidth / (float) frameWidth;
        float backdropH = hostHeight / (float) frameHeight;
        float backdropY = 1f - (top + backdropH);

        int hostRight = hostLeft + hostWidth;
        int hostBottom = hostTop + hostHeight;
        int frameRight = frameLeft + frameWidth;
        int frameBottom = frameTop + frameHeight;

        int intersectionLeft = Math.max(hostLeft, frameLeft);
        int intersectionTop = Math.max(hostTop, frameTop);
        int intersectionRight = Math.min(hostRight, frameRight);
        int intersectionBottom = Math.min(hostBottom, frameBottom);

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            out.set(backdropX, backdropY, backdropW, backdropH,
                    0f, 0f, 0f, 0f, Coverage.OUTSIDE);
            return;
        }

        float validLeft = clamp01((intersectionLeft - hostLeft) / (float) hostWidth);
        float validRight = clamp01((intersectionRight - hostLeft) / (float) hostWidth);

        // Android screen coordinates are top-left based while the Dock-local shader UV is
        // bottom-left based. Convert the visible top-down host interval into GL UV coordinates.
        float validBottom = clamp01(1f - (intersectionBottom - hostTop) / (float) hostHeight);
        float validTop = clamp01(1f - (intersectionTop - hostTop) / (float) hostHeight);

        boolean full = intersectionLeft == hostLeft
                && intersectionTop == hostTop
                && intersectionRight == hostRight
                && intersectionBottom == hostBottom;
        out.set(
                backdropX, backdropY, backdropW, backdropH,
                validLeft, validBottom, validRight, validTop,
                full ? Coverage.FULL : Coverage.PARTIAL);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}