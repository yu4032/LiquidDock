package com.hellovoid.liquiddock;

/** Immutable normalized Grid installation bundle produced once at Launcher composition time. */
final class LauncherGridInstallConfig {
    final HomeGridInstallConfig home;
    final HomeGridWorkstationGeometryConfig workstation;

    private LauncherGridInstallConfig(
            HomeGridInstallConfig home, HomeGridWorkstationGeometryConfig workstation) {
        this.home = home;
        this.workstation = workstation;
    }

    static LauncherGridInstallConfig from(LiquidDockConfig config, float density) {
        LiquidDockConfig.Grid grid = config.grid;
        // Current grid geometry stores dp offsets from vendor baselines.
        float gridScale = density;
        float landLeft = grid.landscapeHorizontal;
        float landRight = grid.landscapeHorizontal;
        float landTop = grid.landscapeTop;
        float landBottom = grid.landscapeBottom;
        float portLeft = grid.portraitHorizontal;
        float portRight = grid.portraitHorizontal;
        float portTop = grid.portraitTop;
        float portBottom = grid.portraitBottom;
        float landGap = grid.landscapeRowGap;
        float portGap = grid.portraitRowGap;

        HomeGridInstallConfig home = new HomeGridInstallConfig(
                grid.enabled,
                grid.columns,
                grid.rows,
                Math.round(landLeft * gridScale),
                Math.round(landRight * gridScale),
                Math.round(landTop * gridScale),
                Math.round(landBottom * gridScale),
                Math.round(portLeft * gridScale),
                Math.round(portRight * gridScale),
                Math.round(portTop * gridScale),
                Math.round(portBottom * gridScale),
                Math.round(landGap * gridScale),
                Math.round(portGap * gridScale),
                Math.round(grid.landscapeIndicatorY * gridScale),
                Math.round(grid.portraitIndicatorY * gridScale),
                Math.round(grid.splitHorizontalOffset * gridScale),
                density,
                grid.widgetHorizontalStretch);

        HomeGridWorkstationGeometryConfig workstation =
                new HomeGridWorkstationGeometryConfig(
                        Math.round(config.workstation.gridHorizontalOffset * gridScale),
                        Math.round(config.workstation.allAppsLandscapeHorizontalOffset * density),
                        Math.round(config.workstation.allAppsLandscapeTopSpacing * density),
                        Math.round(config.workstation.allAppsLandscapeBottomSpacing * density),
                        Math.round(config.workstation.allAppsPortraitHorizontalOffset * density),
                        Math.round(config.workstation.allAppsPortraitTopSpacing * density),
                        Math.round(config.workstation.allAppsPortraitBottomSpacing * density));
        return new LauncherGridInstallConfig(home, workstation);
    }
}
