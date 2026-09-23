package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.GridProfileConfig;
import com.hellovoid.liquiddock.config.LegacyConfigMigration;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 entry point with process-specific timing and opt-in glass integrations. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private String loadedProcessName;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        loadedProcessName = param.getProcessName();
        Api101Bridge.log("[DC] API101 module loaded process=" + loadedProcessName
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            try {
                ClassLoader classLoader = param.getClassLoader();
                if (classLoader == null) return;
                SystemUiKeyguardGoneSource.install(classLoader);
                SystemUiHomeTransitionSource.install(classLoader);
                SystemUiHandleMenuBackdropProbe.install(classLoader);
                ConfigReader configReader = ConfigReader.load();
                LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
                Api101Bridge.log("[DC][SystemUiHandleMenuProbe] config enabled="
                        + runtimeConfig.enabled
                        + " glass=" + runtimeConfig.glass.enabled
                        + " handleMenu=" + runtimeConfig.glass.systemUiHandleMenuEnabled);
                if (runtimeConfig.enabled && runtimeConfig.glass.enabled
                        && runtimeConfig.glass.systemUiHandleMenuEnabled) {
                    SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass);
                }
            } catch (Throwable error) {
                Api101Bridge.log("[DC] SystemUI integration init failed", error);
            }
            return;
        }
        if (SecurityCenterProcessPolicy.PACKAGE.equals(packageName)) {
            if (!SecurityCenterProcessPolicy.shouldInstall(packageName, loadedProcessName)) return;
            try {
                ClassLoader classLoader = param.getClassLoader();
                if (classLoader == null) return;
                ConfigReader configReader = ConfigReader.load();
                LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
                SecurityCenterGlassRuntimeState.initialize(
                        Api101Bridge.remotePreferences("config"),
                        runtimeConfig.enabled,
                        runtimeConfig.glass.enabled,
                        runtimeConfig.glass.securityCenterEnabled);
                if (!SecurityCenterPassBlurContinuousAuthority.install()) {
                    Api101Bridge.log(
                            "[DC][SecurityCenterGlass] continuous PassBlur authority unavailable; fail closed");
                    return;
                }
                if (!SecurityCenterVendorMaterialState.install()) {
                    Api101Bridge.log(
                            "[DC][SecurityCenterGlass] vendor material interception unavailable; fail closed");
                    return;
                }
                if (!SecurityCenterSourceAuthorityHook.install(classLoader)) {
                    Api101Bridge.log(
                            "[DC][SecurityCenterGlass] activity authority unavailable; continuing with vendor-hosted lifecycle");
                }
                SecurityCenterGlassHook.install(classLoader, runtimeConfig);
            } catch (Throwable error) {
                Api101Bridge.log("[DC] Security Center glass init failed", error);
            }
            return;
        }
        if (ThirdPartyGlassAdapterRegistry.handles(packageName)) {
            try {
                ClassLoader classLoader = param.getClassLoader();
                if (classLoader == null) return;
                if (!ThirdPartyGlassAdapterRegistry.install(packageName, classLoader)) {
                    Api101Bridge.log("[DC][ThirdPartyGlass] adapter install failed package=" + packageName);
                }
            } catch (Throwable error) {
                Api101Bridge.log("[DC][ThirdPartyGlass] init failed package=" + packageName, error);
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
                    runtimeConfig.glass.functionalDockIconEnabled,
                    runtimeConfig.glass.recentsCapsuleEnabled,
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
            Launcher450IconSizeHook.install(classLoader,
                    runtimeConfig.enabled && runtimeConfig.grid.iconSizeEnabled,
                    runtimeConfig.grid.iconSizePercent);
            Launcher450DockFunctionalIconRegistry.install(classLoader);
            new MainHook().install(classLoader);

            HomeGridProfile selectedProfile = HomeGridProfile.fromPersisted(
                    GridProfileConfig.normalizeProfile(configReader.s(
                            ConfigSchema.Grid.PROFILE.name(),
                            ConfigSchema.Grid.PROFILE.runtimeFallback())));
            boolean customGridEnabled = runtimeConfig.enabled && runtimeConfig.grid.enabled;

            MiuixLauncherDragOverlayHook.install(classLoader, runtimeConfig);
            MiuixFolderGlassHook.install(classLoader, runtimeConfig);
            MiuixShortcutMenuGlassHook.install(classLoader, runtimeConfig);
            LauncherMamlRootLoadedHook.install(classLoader);
            MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
            DockIconAnimationGlassHook.install(classLoader, runtimeConfig);
            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);
            SystemUiKeyguardGoneRuntime.install();
            SystemUiHomeTransitionRuntime.install();
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
