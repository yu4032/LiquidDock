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
        boolean dp = grid.dp;
        boolean offsets = grid.offsets;
        float gridScale = dp ? density : 1f;
        int landXBase = dp ? 57 : 160;
        int landYBase = dp ? 28 : 80;
        int portXBase = dp ? 28 : 80;
        int portYBase = dp ? 57 : 160;

        float landLeft = offsets ? grid.landscapeHorizontal
                : landXBase + grid.landscapeHorizontal;
        float landRight = offsets ? grid.landscapeHorizontal
                : landXBase + grid.landscapeHorizontal;
        float landTop = offsets ? grid.landscapeTop : landYBase + grid.landscapeTop;
        float landBottom = offsets ? grid.landscapeBottom
                : landYBase + grid.landscapeBottom;
        float portLeft = offsets ? grid.portraitHorizontal
                : portXBase + grid.portraitHorizontal;
        float portRight = offsets ? grid.portraitHorizontal
                : portXBase + grid.portraitHorizontal;
        float portTop = offsets ? grid.portraitTop : portYBase + grid.portraitTop;
        float portBottom = offsets ? grid.portraitBottom
                : portYBase + grid.portraitBottom;
        float landGap = grid.landscapeRowGap;
        float portGap = grid.portraitRowGap;
        if (!offsets) {
            landLeft -= landXBase;
            landRight -= landXBase;
            landTop -= landYBase;
            landBottom -= landYBase;
            portLeft -= portXBase;
            portRight -= portXBase;
            portTop -= portYBase;
            portBottom -= portYBase;
            landGap -= dp ? 1 : 3;
            portGap -= dp ? 1 : 3;
        }

        HomeGridInstallConfig home = new HomeGridInstallConfig(
                grid.enabled,
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
                density);

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
