package com.hellovoid.liquiddock;

/**
 * One frame policy for settled 2x2 native folders. The two occupied CellLayout
 * cells are authoritative; transient drag/fullscreen dimensions are never input.
 */
final class NativeFourFolderFramePolicy {
    static final class Frame {
        final int left, top, width, height;

        Frame(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    private NativeFourFolderFramePolicy() {}

    static Frame resolve(int[] xs, int[] ys, int cellX, int cellY,
            int cellWidth, int cellHeight, int parentWidth, int parentHeight,
            int leftMargin, int topMargin, int rightMargin, int bottomMargin) {
        if (xs == null || ys == null || cellX < 0 || cellY < 0
                || cellX >= xs.length - 1 || cellY >= ys.length - 1
                || cellWidth <= 0 || cellHeight <= 0
                || parentWidth <= 0 || parentHeight <= 0) return null;

        int[] allocation = {
                xs[cellX], ys[cellY],
                xs[cellX + 1] + cellWidth - xs[cellX],
                ys[cellY + 1] + cellHeight - ys[cellY]
        };
        int[] frame = WidgetGridSizing.centeredFrame(allocation,
                leftMargin, topMargin, rightMargin, bottomMargin);
        if (frame[2] <= 0 || frame[3] <= 0
                || frame[0] < 0 || frame[1] < 0
                || (long) frame[0] + frame[2] > parentWidth
                || (long) frame[1] + frame[3] > parentHeight) return null;

        return new Frame(frame[0], frame[1], frame[2], frame[3]);
    }
}
