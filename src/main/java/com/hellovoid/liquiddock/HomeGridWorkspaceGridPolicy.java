package com.hellovoid.liquiddock;

/** Logical HOME grid shape selected for each vendor workspace profile. */
final class HomeGridWorkspaceGridPolicy {
    private HomeGridWorkspaceGridPolicy() {}

    static int[] fullScreenCounts(
            String name, int landscapeColumns, int landscapeRows) {
        if (landscapeColumns <= 0 || landscapeRows <= 0) return null;
        if ("land_grid".equals(name)) {
            return new int[]{landscapeColumns, landscapeRows};
        }
        if ("vertical_grid".equals(name)) {
            return new int[]{landscapeRows, landscapeColumns};
        }
        return null;
    }

    static int[] splitCounts(int landscapeColumns, int landscapeRows) {
        if (landscapeColumns <= 0 || landscapeRows <= 0) return null;
        return new int[]{landscapeRows, landscapeColumns};
    }

    static boolean isSplitGridName(String name) {
        return "land_split_grid".equals(name);
    }
}
