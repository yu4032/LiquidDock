package com.hellovoid.liquiddock;

/** Pure geometry for the HyperOS 4-style 2x1 folder preview. */
final class Folder2x1LayoutPolicy {
    static final int AGGREGATE_THRESHOLD = 3;
    static final int MAX_VISIBLE_CHILDREN = 6;
    private static final float LARGE_ICON_SCALE = 0.52f;
    private static final float MINI_GAP_SCALE = 0.08f;

    private Folder2x1LayoutPolicy() {}

    static Layout resolve(int workspaceIconSizePx, int contentCount, int childCount) {
        int icon = Math.max(1, workspaceIconSizePx);
        int width = icon * 2;
        int height = icon;
        int children = Math.max(0, childCount);
        int contents = Math.max(0, contentCount);
        Slot[] slots = new Slot[children];

        if (children == 0 || contents == 0) {
            return new Layout(width, height, false, 0, slots);
        }

        int large = Math.max(1, Math.round(icon * LARGE_ICON_SCALE));
        boolean aggregate = contents > AGGREGATE_THRESHOLD;
        if (!aggregate) {
            int visible = Math.min(Math.min(contents, AGGREGATE_THRESHOLD), children);
            int gap = Math.max(0, (width - (visible * large)) / (visible + 1));
            int top = (height - large) / 2;
            for (int i = 0; i < visible; i++) {
                int left = gap + (i * (large + gap));
                slots[i] = new Slot(left, top, left + large, top + large);
            }
            return new Layout(width, height, false, visible, slots);
        }

        int visible = Math.min(Math.min(contents, MAX_VISIBLE_CHILDREN), children);
        int gap = Math.max(0, (width - (AGGREGATE_THRESHOLD * large))
                / (AGGREGATE_THRESHOLD + 1));
        int top = (height - large) / 2;
        for (int i = 0; i < Math.min(2, visible); i++) {
            int left = gap + (i * (large + gap));
            slots[i] = new Slot(left, top, left + large, top + large);
        }

        int clusterLeft = gap + (2 * (large + gap));
        int miniGap = Math.max(1, Math.round(large * MINI_GAP_SCALE));
        int mini = Math.max(1, (large - miniGap) / 2);
        for (int i = 2; i < visible; i++) {
            int clusterIndex = i - 2;
            int col = clusterIndex % 2;
            int row = clusterIndex / 2;
            int left = clusterLeft + (col * (mini + miniGap));
            int childTop = top + (row * (mini + miniGap));
            slots[i] = new Slot(left, childTop, left + mini, childTop + mini);
        }
        return new Layout(width, height, true, visible, slots);
    }

    static final class Layout {
        final int width;
        final int height;
        final boolean aggregateThirdSlot;
        final int visibleChildren;
        final Slot[] slots;

        Layout(int width, int height, boolean aggregateThirdSlot,
               int visibleChildren, Slot[] slots) {
            this.width = width;
            this.height = height;
            this.aggregateThirdSlot = aggregateThirdSlot;
            this.visibleChildren = visibleChildren;
            this.slots = slots;
        }
    }

    static final class Slot {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Slot(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() { return right - left; }
        int height() { return bottom - top; }
    }
}
