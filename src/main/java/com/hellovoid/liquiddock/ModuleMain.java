package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.GridProfileConfig;
import com.hellovoid.liquiddock.config.LegacyConfigMigration;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 entry point. SystemUI is injected only as a read-only unlock event source. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        Api101Bridge.log("[DC] API101 module loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            try {
                SystemUiKeyguardGoneSource.install(param.getClassLoader());
            } catch (Throwable error) {
                Api101Bridge.log("[DC] SystemUI unlock source init failed", error);
            }
            return;
        }
        if (!LAUNCHER_PACKAGE.equals(packageName)) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            ConfigReader configReader = ConfigReader.load();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
            AnimationRuntimeState.configure(runtimeConfig.animation);
            GlassRuntimeState.initialize(Api101Bridge.remotePreferences("config"),
                    runtimeConfig.enabled && runtimeConfig.glass.enabled,
                    runtimeConfig.glass.iconEnabled,
                    runtimeConfig.glass.widgetEnabled,
                    runtimeConfig.glass.widgetDarkContent,
                    runtimeConfig.glass.smallFolderStyle.enabled,
                    runtimeConfig.glass.largeFolderStyle.enabled);
            VisualRuntimeState.initialize(Api101Bridge.remotePreferences("config"),
                    runtimeConfig.enabled,
                    runtimeConfig.dock.enabled,
                    runtimeConfig.dock.strokeEnabled,
                    runtimeConfig.dock.shadowEnabled,
                    runtimeConfig.dock.strokeShadow,
                    runtimeConfig.divider.enabled);
            DockMirrorShortcutHook.install(classLoader);
            DockNativeShadowBridge.install(classLoader, runtimeConfig.dock);
            new MainHook().install(classLoader);

            HomeGridProfile selectedProfile = HomeGridProfile.fromPersisted(
                    GridProfileConfig.normalizeProfile(configReader.s(
                            GridProfileConfig.PROFILE_KEY, GridProfileConfig.DEFAULT_PROFILE)));
            boolean customGridEnabled = runtimeConfig.enabled && runtimeConfig.grid.enabled;

            MiuixLauncherDragOverlayHook.install(classLoader, runtimeConfig);
            MiuixFolderGlassHook.install(classLoader, runtimeConfig);
            LauncherMamlRootLoadedHook.install(classLoader);
            MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
            DockIconAnimationGlassHook.install(classLoader, runtimeConfig);
            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);
            SystemUiKeyguardGoneRuntime.install();
            LauncherGlassHomePresentationHook.install(classLoader);
            DockGlassDropRefreshHook.install(classLoader);
            RecentsBackgroundBlurHook.install(classLoader, runtimeConfig);
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
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
