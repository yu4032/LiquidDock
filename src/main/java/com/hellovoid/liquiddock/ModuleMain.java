package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.LegacyConfigMigration;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 experiment; do not hot-reload active process hooks before owner teardown exists. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private String loadedProcessName;
    // True once target-process initialization can own hooks, listeners or EGL resources.
    private boolean activeProcessLifecycle;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        refreshDebugLogging();
        loadedProcessName = param.getProcessName();
        Api101Bridge.log("[DC] API102 module loaded process=" + loadedProcessName
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
        if ("system".equals(loadedProcessName)) {
            activeProcessLifecycle = true;
            try {
                ConfigReader config = ConfigReader.load();
                boolean enabled = config.b(
                        ConfigSchema.Core.ENABLED.name(),
                        ConfigSchema.Core.ENABLED.runtimeFallback());
                boolean liquidGlassEnabled = config.b(
                        ConfigSchema.Glass.ENABLED.name(),
                        ConfigSchema.Glass.ENABLED.runtimeFallback());
                boolean wallpaperFlickerFixEnabled = config.b(
                        ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.name(),
                        ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.runtimeFallback());
                if (enabled && liquidGlassEnabled && wallpaperFlickerFixEnabled) {
                    WallpaperClientCompositionHook.install();
                }
            } catch (Throwable error) {
                Api101Bridge.log("[DC][WallpaperClientComposition] config gate failed", error);
            }
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        refreshDebugLogging();
        String packageName = param.getPackageName();
        // Lifecycle callbacks are not replayed automatically after API 102 hot reload.
        // These owners currently have no complete stop/unregister/restore transaction.
        if (SYSTEM_UI_PACKAGE.equals(packageName)
                || LAUNCHER_PACKAGE.equals(packageName)
                || SecurityCenterProcessPolicy.PACKAGE.equals(packageName)
                || ThirdPartyGlassAdapterRegistry.handles(packageName)) {
            activeProcessLifecycle = true;
        }
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            ClassLoader classLoader = param.getClassLoader();
            if (classLoader == null) return;

            // Gesture-handle ownership is independent from keyguard/glass integrations. Keep its
            // bootstrap isolated so an unrelated SystemUI compatibility failure cannot suppress it.
            try {
                GestureHandleRuntimeState.initialize(
                        Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP));
                SystemUiGestureHandleFadeHook.install(classLoader);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][GestureHandle] SystemUI bootstrap failed", error);
            }

            try {
                SystemUiKeyguardGoneSource.install(classLoader);
                ConfigReader configReader = ConfigReader.load();
                LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
                if (runtimeConfig.enabled && runtimeConfig.glass.enabled
                        && runtimeConfig.glass.systemUiHandleMenuEnabled) {
                    if (SystemUiHandleMenuSurfaceAnimationAuthority.install()) {
                        SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass);
                    } else {
                        Api101Bridge.log(
                                "[DC][SystemUiHandleMenuGlass] surface animation authority unavailable; fail closed");
                    }
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
                AnimationRuntimeState.configure(runtimeConfig.animation);
                boolean sideSlideEnabled = SideSlideHoldFeatureConfig.isEnabled(configReader);
                if (runtimeConfig.enabled && sideSlideEnabled) {
                    SecurityCenterSidebarCommandBridge.install(classLoader);
                }
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
            // Legacy migration can introduce the debug preference during this same Launcher start.
            refreshDebugLogging();
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
            if (runtimeConfig.enabled && SideSlideHoldFeatureConfig.isEnabled(configReader)) {
                Launcher450SideSlideHoldHook.install(classLoader);
            }
            Launcher450DockFunctionalIconRegistry.install(classLoader);
            new MainHook().install(classLoader);

            MiuixLauncherDragOverlayHook.install(classLoader, runtimeConfig);
            MiuixFolderGlassHook.install(classLoader, runtimeConfig);
            MiuixShortcutMenuGlassHook.install(classLoader, runtimeConfig);
            LauncherUninstallDialogGlassHook.install(classLoader, runtimeConfig);
            LauncherMamlRootLoadedHook.install(classLoader);
            MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
            DockIconAnimationGlassHook.install(classLoader, runtimeConfig);
            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);
            SystemUiKeyguardGoneRuntime.install();
            LauncherGlassHomePresentationHook.install(classLoader);
            DockGlassDropRefreshHook.install(classLoader);
            RecentsBackgroundBlurHook.install(classLoader, runtimeConfig);
            DockBottomGeometryHook.install(classLoader);
        } catch (Throwable error) {
            Api101Bridge.errorAlways("[DC] API102 package init failed", error);
        }
    }

    /**
     * API 102 lifecycle probe. An active LiquidDock process may hold native PassBlur producers,
     * ViewTreeObserver callbacks, BroadcastReceivers and hook closures that refer to this
     * module generation. Returning true before those resources are detached would be unsafe.
     *
     * The only eligible window is before target-package initialization; automatic hot reload
     * remains disabled in module.prop until the complete ownership transaction is implemented.
     */
    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        if (activeProcessLifecycle || Api102HookRegistry.installedCount() != 0) {
            Api101Bridge.log("[DC][HotReload] rejected: active owners or identified hooks"
                    + " (hookCount=" + Api102HookRegistry.installedCount() + ")");
            return false;
        }
        param.setSavedInstanceState(loadedProcessName);
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        // Only used for the safe pre-install path admitted above.
        Api101Bridge.init(this);
        Object previous = param.getSavedInstanceState();
        loadedProcessName = previous instanceof String ? (String) previous : null;
        refreshDebugLogging();
        Api101Bridge.log("[DC][HotReload] restored pre-install module state");
    }

    private static void refreshDebugLogging() {
        boolean enabled = ConfigSchema.Debug.LOGGING.runtimeFallback();
        try {
            enabled = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP).getBoolean(
                    ConfigSchema.Debug.LOGGING.name(),
                    ConfigSchema.Debug.LOGGING.runtimeFallback());
        } catch (Throwable ignored) {
            // Logging must fail closed. ConfigReader will retain its normal runtime fallback path.
        }
        Api101Bridge.setDebugLogging(enabled);
    }
}
