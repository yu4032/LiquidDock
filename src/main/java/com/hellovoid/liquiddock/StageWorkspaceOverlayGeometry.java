package com.hellovoid.liquiddock;

/** Pure geometry authority for the fixed Stage overlay. */
final class StageWorkspaceOverlayGeometry {
    private StageWorkspaceOverlayGeometry() {}

    static final class Geometry {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Geometry(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int left() { return left; }
        int top() { return top; }
        int right() { return right; }
        int bottom() { return bottom; }
        int width() { return right - left; }
        int height() { return bottom - top; }
    }

    static Geometry resolve(HomeGridProfile profile,
                            int columns,
                            int rows,
                            boolean normalHomeLandscape,
                            int[] physicalXs,
                            int top,
                            int bottom) {
        if (!StageWorkspacePolicy.isSupported(
                profile, columns, rows, normalHomeLandscape)) {
            return null;
        }
        if (physicalXs == null || physicalXs.length != StageWorkspacePolicy.PHYSICAL_COLUMNS) {
            return null;
        }
        for (int index = 1; index < physicalXs.length; index++) {
            if (physicalXs[index] <= physicalXs[index - 1]) return null;
        }
        if (bottom <= top) return null;

        int left = physicalXs[0];
        int right = physicalXs[StageWorkspacePolicy.STAGE_COLUMNS];
        if (right <= left) return null;
        return new Geometry(left, top, right, bottom);
    }
}
