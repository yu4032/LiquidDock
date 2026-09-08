package com.hellovoid.liquiddock;

/** Immutable registry of Widget spans supported by the custom grid adaptation path. */
final class WidgetSpecRegistry {
    static final WidgetSpecRegistry DEFAULT = new WidgetSpecRegistry(
            new int[][]{{1, 1}, {2, 1}, {2, 2}, {4, 2}});

    private final int[][] specs;

    private WidgetSpecRegistry(int[][] specs) {
        this.specs = new int[specs.length][2];
        for (int i = 0; i < specs.length; i++) {
            this.specs[i][0] = specs[i][0];
            this.specs[i][1] = specs[i][1];
        }
    }

    boolean supports(int spanX, int spanY) {
        for (int[] spec : specs) {
            if (spec[0] == spanX && spec[1] == spanY) return true;
        }
        return false;
    }
}
