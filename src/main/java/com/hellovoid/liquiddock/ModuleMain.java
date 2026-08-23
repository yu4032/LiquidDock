package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.GridProfileConfig;
import com.hellovoid.liquiddock.config.LegacyConfigMigration;
import com.hellovoid.liquiddock.config.SidebarGlassConfig;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 entry point for Launcher and the opt-in SecurityCenter sidebar domain. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SECURITY_CENTER_PACKAGE = SidebarGlassPolicy.SECURITY_CENTER_PACKAGE;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        Api101Bridge.log("[DC] API101 module loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!LAUNCHER_PACKAGE.equals(packageName)
                && !SECURITY_CENTER_PACKAGE.equals(packageName)) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            ConfigReader configReader = ConfigReader.load();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);

            if (SECURITY_CENTER_PACKAGE.equals(packageName)) {
                boolean sidebarEnabled = configReader.b(
                        SidebarGlassConfig.ENABLED.name(),
                        SidebarGlassConfig.ENABLED.runtimeFallback());
                boolean liquidEnabled = runtimeConfig.enabled && runtimeConfig.glass.enabled;
                boolean installSidebar = SidebarGlassPolicy.shouldInstall(
                        packageName, liquidEnabled, sidebarEnabled);
                MainHook.debugLogging = runtimeConfig.debugLog;
                // Sidebar material replacement is intentionally process-start scoped for the
                // first experiment.  A half-live teardown would clear Prismal while leaving the
                // vendor backgrounds suppressed, so GUI changes explicitly require a restart.
                GlassRuntimeState.initialize(null, installSidebar);
                if (installSidebar) {
                    SecurityCenterSidebarGlassHook.install(classLoader, runtimeConfig);
                } else {
                    MainHook.log("[DC][SidebarGlass] disabled by config");
                }
                return;
            }

            GlassRuntimeState.initialize(Api101Bridge.remotePreferences("config"),
                    runtimeConfig.enabled && runtimeConfig.glass.enabled);
            new MainHook().install(classLoader);

            HomeGridProfile selectedProfile = HomeGridProfile.fromPersisted(
                    GridProfileConfig.normalizeProfile(configReader.s(
                            GridProfileConfig.PROFILE_KEY, GridProfileConfig.DEFAULT_PROFILE)));
            boolean customGridEnabled = runtimeConfig.enabled && runtimeConfig.grid.enabled;

            MiuixLauncherDragOverlayHook.install(classLoader, runtimeConfig);
            MiuixFolderGlassHook.install(classLoader, runtimeConfig);
            MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);
            DockBottomGeometryHook.install(classLoader);
            HomeGridProfileOverlayHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridOrientationMemoryHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridMutationCaptureHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridDeviceConfigCountHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridHorizontalCenteringHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridVerticalBoundsHook.install(classLoader,
                    customGridEnabled, selectedProfile, runtimeConfig.grid);
            WorkspaceDropRuleHook.install(classLoader, customGridEnabled, selectedProfile);
            HomeGridDragBoundsHook.install(classLoader,
                    customGridEnabled, selectedProfile);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed package=" + packageName, error);
        }
    }
}
