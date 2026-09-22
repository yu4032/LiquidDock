package com.hellovoid.liquiddock;

/** Immutable Workstation-specific grid geometry; kept separate from normal HOME install config. */
final class HomeGridWorkstationGeometryConfig {
    static final HomeGridWorkstationGeometryConfig NONE =
            new HomeGridWorkstationGeometryConfig(0, 0, 0, 0, 0, 0, 0);

    final int workspaceHorizontalOffset;
    final int allAppsLandscapeHorizontal;
    final int allAppsLandscapeTop;
    final int allAppsLandscapeBottom;
    final int allAppsPortraitHorizontal;
    final int allAppsPortraitTop;
    final int allAppsPortraitBottom;

    HomeGridWorkstationGeometryConfig(
            int workspaceHorizontalOffset,
            int allAppsLandscapeHorizontal,
            int allAppsLandscapeTop,
            int allAppsLandscapeBottom,
            int allAppsPortraitHorizontal,
            int allAppsPortraitTop,
            int allAppsPortraitBottom) {
        this.workspaceHorizontalOffset = workspaceHorizontalOffset;
        this.allAppsLandscapeHorizontal = allAppsLandscapeHorizontal;
        this.allAppsLandscapeTop = allAppsLandscapeTop;
        this.allAppsLandscapeBottom = allAppsLandscapeBottom;
        this.allAppsPortraitHorizontal = allAppsPortraitHorizontal;
        this.allAppsPortraitTop = allAppsPortraitTop;
        this.allAppsPortraitBottom = allAppsPortraitBottom;
    }
}
