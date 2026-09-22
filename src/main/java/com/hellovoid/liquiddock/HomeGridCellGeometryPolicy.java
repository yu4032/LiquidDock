package com.hellovoid.liquiddock;

/** Android-free geometry policy for one vendor CellLayout snapshot. */
final class HomeGridCellGeometryPolicy {
    static final class Input {
        final HomeGridInstallConfig install;
        final HomeGridWorkstationGeometryConfig workstation;
        final boolean portrait;
        final boolean workstationActive;
        final boolean workstationAllApps;
        final int width;
        final int height;
        final int countX;
        final int countY;
        final int baseCell;
        final int configLeft;
        final int baseTop;
        final int baseWidthGap;
        final int baseHeightGap;
        final int dockBarHeight;

        Input(
                HomeGridInstallConfig install,
                HomeGridWorkstationGeometryConfig workstation,
                boolean portrait,
                boolean workstationActive,
                boolean workstationAllApps,
                int width,
                int height,
                int countX,
                int countY,
                int baseCell,
                int configLeft,
                int baseTop,
                int baseWidthGap,
                int baseHeightGap,
                int dockBarHeight) {
            this.install = install;
            this.workstation = workstation == null
                    ? HomeGridWorkstationGeometryConfig.NONE : workstation;
            this.portrait = portrait;
            this.workstationActive = workstationActive;
            this.workstationAllApps = workstationAllApps;
            this.width = width;
            this.height = height;
            this.countX = countX;
            this.countY = countY;
            this.baseCell = baseCell;
            this.configLeft = configLeft;
            this.baseTop = baseTop;
            this.baseWidthGap = baseWidthGap;
            this.baseHeightGap = baseHeightGap;
            this.dockBarHeight = dockBarHeight;
        }
    }

    static final class Result {
        final int left;
        final int right;
        final int top;
        final int bottom;
        final int cellSize;
        final int widthGap;
        final int heightGap;

        Result(int left, int right, int top, int bottom,
               int cellSize, int widthGap, int heightGap) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.cellSize = cellSize;
            this.widthGap = widthGap;
            this.heightGap = heightGap;
        }
    }

    private HomeGridCellGeometryPolicy() {}

    static Result calculate(Input in) {
        if (in == null || in.install == null || in.countX <= 0 || in.countY <= 0
                || in.baseCell <= 0 || in.width <= 0 || in.height <= 0) {
            return null;
        }

        int baseLeft = in.configLeft
                - Math.max(0, in.countX - 1) * (in.baseWidthGap / 2);
        int baseTop = in.baseTop;
        int baseWidthGap = in.baseWidthGap;
        int baseHeightGap = in.baseHeightGap;

        if (in.workstationAllApps) {
            baseLeft = Math.max(0, in.configLeft);
            baseTop = Math.max(0, baseTop);
            baseWidthGap = Math.max(0, in.baseWidthGap);
            baseHeightGap = Math.max(0, in.baseHeightGap);
        } else if (in.install.enabled) {
            int contentHeight = Math.max(
                    in.baseCell * in.countY,
                    in.height - Math.min(in.height, Math.max(0, in.dockBarHeight)));
            baseWidthGap = 0;
            baseHeightGap = Math.max(1, Math.round(in.install.density));
            baseLeft = Math.max(0, (in.width - in.baseCell * in.countX) / 2);
            baseTop = Math.max(0, (contentHeight - in.baseCell * in.countY
                    - baseHeightGap * Math.max(0, in.countY - 1)) / 2);
        }

        int baseRight = in.width - (baseLeft + in.baseCell * in.countX
                + baseWidthGap * Math.max(0, in.countX - 1));
        int baseBottom = in.height - (baseTop + in.baseCell * in.countY
                + baseHeightGap * Math.max(0, in.countY - 1));
        if (in.workstationAllApps) {
            baseRight = Math.max(0, baseRight);
            baseBottom = Math.max(0, baseBottom);
        }

        int left;
        int right;
        int top;
        int bottom;
        if (in.workstationAllApps) {
            int horizontalMargin = in.portrait
                    ? in.workstation.allAppsPortraitHorizontal
                    : in.workstation.allAppsLandscapeHorizontal;
            int topMargin = in.portrait
                    ? in.workstation.allAppsPortraitTop
                    : in.workstation.allAppsLandscapeTop;
            int bottomMargin = in.portrait
                    ? in.workstation.allAppsPortraitBottom
                    : in.workstation.allAppsLandscapeBottom;
            int[] margins = WorkstationGridMarginPolicy.apply(
                    baseLeft, baseRight, baseTop, baseBottom,
                    horizontalMargin, topMargin, bottomMargin);
            left = margins[0];
            right = margins[1];
            top = margins[2];
            bottom = margins[3];
        } else if (in.workstationActive) {
            int workstationX = Math.max(
                    -baseLeft,
                    Math.min(baseRight, in.workstation.workspaceHorizontalOffset));
            left = baseLeft + workstationX;
            right = baseRight - workstationX;
            top = baseTop;
            bottom = baseBottom;
        } else {
            HomeGridInstallConfig.Orientation offsets = in.install.orientation(in.portrait);
            left = baseLeft + offsets.left;
            right = baseRight + offsets.right;
            top = baseTop + offsets.top;
            bottom = baseBottom + offsets.bottom;
        }

        HomeGridInstallConfig.Orientation offsets = in.install.orientation(in.portrait);
        int rowGap = baseHeightGap + (in.workstationActive || in.workstationAllApps
                ? 0 : offsets.rowGap);
        int availableWidth = Math.max(in.countX, in.width - left - right);
        int allAppsInnerHeight = Math.max(in.countY, in.height - top - bottom);
        int availableHeight = in.workstationAllApps
                ? allAppsInnerHeight
                : allAppsInnerHeight - rowGap * Math.max(0, in.countY - 1);
        int cellSize = Math.min(in.baseCell, Math.min(
                Math.max(1, availableWidth / in.countX),
                Math.max(1, availableHeight / in.countY)));
        int widthGap = in.countX > 1
                ? Math.max(0, availableWidth - cellSize * in.countX) / (in.countX - 1)
                : 0;
        int heightGap = rowGap;
        if (in.workstationAllApps && in.countY > 1) {
            heightGap = Math.max(0, allAppsInnerHeight - cellSize * in.countY)
                    / (in.countY - 1);
        }
        return new Result(left, right, top, bottom, cellSize, widthGap, heightGap);
    }

    static boolean sizeMatchesOrientation(boolean portrait, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        return portrait ? height >= width : width >= height;
    }
}
