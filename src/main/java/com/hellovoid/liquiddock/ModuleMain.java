package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;

import android.content.SharedPreferences;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point; hot reload remains disabled for owned process resources. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private String loadedProcessName;
    private final Api102ReloadGate reloadGate = new Api102ReloadGate();

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        refreshDebugLogging();
        loadedProcessName = param.getProcessName();
        Api101Bridge.log("[DC] API102 module loaded process=" + loadedProcessName
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
        if ("system".equals(loadedProcessName)) {
            // Acquiring a wallpaper listener or renderer makes this process non-reloadable.
            if (!reloadGate.enterOwnerLifecycle()) return;
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
                WallpaperClientCompositionHook.initialize(
                        Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP),
                        enabled && liquidGlassEnabled && wallpaperFlickerFixEnabled);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][WallpaperClientComposition] config gate failed", error);
            }
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        refreshDebugLogging();
        String packageName = param.getPackageName();
        // Claim ownership before any install operation, including failing/partial installs.
        // In an admitted pre-install handoff the old generation must not start new hooks.
        if ((SYSTEM_UI_PACKAGE.equals(packageName)
                || LAUNCHER_PACKAGE.equals(packageName)
                || SecurityCenterProcessPolicy.PACKAGE.equals(packageName)
                || ThirdPartyGlassAdapterRegistry.handles(packageName))
                && !reloadGate.enterOwnerLifecycle()) {
            return;
        }
        if (SYSTEM_UI_PACKAGE.equals(packageName)) {
            installSystemUi(param.getClassLoader());
            return;
        }
        if (SecurityCenterProcessPolicy.PACKAGE.equals(packageName)) {
            if (!SecurityCenterProcessPolicy.shouldInstall(packageName, loadedProcessName)) return;
            try {
                ClassLoader classLoader = param.getClassLoader();
                if (classLoader == null) return;
                ConfigReader configReader = ConfigReader.load();
                GlassEdgeEffects.refresh(configReader);
                LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
                AnimationRuntimeState.configure(runtimeConfig.animation);
                // Native Sidebar bridge also owns the vendor-handle glass entry.
                // Launcher side-slide extension remains gated separately below.
                if (runtimeConfig.enabled) {
                    SecurityCenterSidebarCommandBridge.install(classLoader);
                }
                SecurityCenterGlassRuntimeState.initialize(
                        Api101Bridge.remotePreferences("config"),
                        runtimeConfig.enabled,
                        runtimeConfig.glass.enabled,
                        runtimeConfig.glass.securityCenterEnabled);
                ExternalGlassLiveConfigState.initialize(packageName);
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
                GlassEdgeEffects.refresh(ConfigReader.load());
                if (!ThirdPartyGlassAdapterRegistry.install(packageName, classLoader)) {
                    Api101Bridge.log("[DC][ThirdPartyGlass] adapter install failed package=" + packageName);
                }
                // This scope is not the Launcher: give its already-open renderers their own
                // Remote Preferences observer, regardless of initially enabled appearance.
                ExternalGlassLiveConfigState.initialize(packageName);
            } catch (Throwable error) {
                Api101Bridge.log("[DC][ThirdPartyGlass] init failed package=" + packageName, error);
            }
            return;
        }
        if (!LAUNCHER_PACKAGE.equals(packageName)) return;
        try {
            initializeCurrentProfileIfEmpty();
            ClassLoader classLoader = param.getClassLoader();
            ConfigReader configReader = ConfigReader.load();
            GlassEdgeEffects.refresh(configReader);
            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
            // Independent opt-in compatibility: its own preference is the sole
            // activation switch, not the global glass/feature master toggle.
            LauncherMingouCompatHook.install(classLoader);
            if (runtimeConfig.enabled) {
                // Folder picker/model hooks are independent of the glass renderers.
                // Establish the OEM background's stroke state before any
                // folder view is inflated or rebound in the Launcher process.
                LargeFolderNativeOutlineHook.install(classLoader);
                // The picker and persisted span conversion are independent of
                // vendor preview measurement APIs. Never hide these controls
                // because an optional 4.50 geometry method is unavailable.
                LauncherLargeFolderPickerHook.install(classLoader);
                LauncherLargeFolderNativeSpanHook.install(classLoader);
                LauncherNativeFourFolderHook.install(classLoader);
            }
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
            LiveGlassConfigState.initialize(
                    Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP), runtimeConfig);
            // Install regardless of initial toggle state, so subsequent changes
            // take effect without rebooting Launcher. No renderer is replaced.
            LauncherWallpaperZoomHook.onLiveConfigChanged(runtimeConfig);
            LauncherWallpaperZoomHook.install(classLoader);
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

    /** Fresh installation and reload both use the same SystemUI integration sequence. */
    private static void installSystemUi(ClassLoader classLoader) {
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
                GlassEdgeEffects.refresh(configReader);
                LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
                // Install once regardless of initial preference. The hook's runtime gate
                // stays inert until the user enables caption-menu glass.
                SystemUiHandleMenuGlassHook.onLiveGlassConfigChanged(configReader, runtimeConfig);
                if (SystemUiHandleMenuSurfaceAnimationAuthority.install()) {
                    SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass);
                } else {
                    Api101Bridge.log(
                            "[DC][SystemUiHandleMenuGlass] surface animation authority unavailable; fail closed");
                }
                ExternalGlassLiveConfigState.initialize(SYSTEM_UI_PACKAGE);
            } catch (Throwable error) {
                Api101Bridge.log("[DC] SystemUI integration init failed", error);
            }
    }

    /** First-install initialization, never reinterpretation of prior persisted keys. */
    private static void initializeCurrentProfileIfEmpty() {
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
            if (remote != null && remote.getAll().isEmpty()) {
                PresetManager.applyDefault(remote.edit());
            }
        } catch (Throwable error) {
            // A storage failure must not block Launcher hooks: snapshot defaults still work.
            Api101Bridge.log("[DC][Config] default profile initialization unavailable", error);
        }
    }

    /** API 102 handoff: SystemUI has a guarded owner retirement; all other scopes remain strict. */
    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        if (SYSTEM_UI_PACKAGE.equals(loadedProcessName)) {
            return retireSystemUiForHotReload(param);
        }
        if (Api102HookRegistry.installedCount() != 0 || !reloadGate.beginHandoff()) {
            Api101Bridge.log("[DC][API102] hot reload rejected: active process owners or HookHandles");
            return false;
        }
        try {
            param.setSavedInstanceState(loadedProcessName);
            return true;
        } catch (Throwable error) {
            reloadGate.abortHandoff();
            Api101Bridge.log("[DC][API102] hot reload state transfer failed", error);
            return false;
        }
    }

    /**
     * Conservative SystemUI-only reload: live caption-menu graphics are not yet reversible.
     * A failed retirement rejects the request and may require a normal SystemUI restart.
     */
    private boolean retireSystemUiForHotReload(HotReloadingParam param) {
        if (getApiVersion() < 102) return false;
        HookUtil.InvocationResult<Object> app =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        if (!app.succeeded() || !(app.value() instanceof android.content.Context)) {
            Api101Bridge.log("[DC][API102] SystemUI reload rejected: no live application");
            return false;
        }
        if (!retireSystemUiOwnersOnMainThread()) return false;
        if (!reloadGate.beginRetiredOwnerHandoff()) {
            Api101Bridge.log("[DC][API102] SystemUI reload rejected: owner gate");
            return false;
        }
        try {
            param.setSavedInstanceState("systemui-reload:" + loadedProcessName);
            return true;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][API102] SystemUI saved-state transfer failed", error);
            return false;
        }
    }

    /**
     * Gesture-handle alpha is a View-owned state: restore it on SystemUI's UI thread.
     * The framework does not guarantee the thread of its hot-reload callback.
     * Cancelling an unstarted task avoids a delayed teardown after rejection.
     */
    private static boolean retireSystemUiOwnersOnMainThread() {
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (main == null) return false;
        if (android.os.Looper.myLooper() == main) return retireSystemUiOwners();
        java.util.concurrent.FutureTask<Boolean> task =
                new java.util.concurrent.FutureTask<>(ModuleMain::retireSystemUiOwners);
        android.os.Handler handler = new android.os.Handler(main);
        if (!handler.post(task)) return false;
        try {
            return task.get(2L, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.cancel(false);
            Api101Bridge.log("[DC][API102] SystemUI UI-thread retirement interrupted", interrupted);
            return false;
        } catch (Throwable error) {
            task.cancel(false);
            // If retirement already started, the old generation stays loaded because we
            // reject this request. An ordinary scope restart restores feature ownership.
            Api101Bridge.log("[DC][API102] SystemUI UI-thread retirement failed", error);
            return false;
        }
    }

    private static boolean retireSystemUiOwners() {
        if (!SystemUiHandleMenuSurfaceAnimationAuthority.isIdleForHotReload()) {
            Api101Bridge.log("[DC][API102] reload rejected: active surface alpha listeners");
            return false;
        }
        if (!SystemUiHandleMenuGlassHook.retireUnusedForHotReload()) {
            Api101Bridge.log("[DC][API102] reload rejected: used caption GPU/View owner");
            return false;
        }
        if (!ExternalGlassLiveConfigState.shutdownForFutureReload()
                || !SystemUiKeyguardGoneSource.stopForFutureReload()
                || !SystemUiGestureHandleFadeHook.stopForFutureReload()) {
            Api101Bridge.log("[DC][API102] reload rejected: owner cleanup incomplete");
            return false;
        }
        Api102HookRegistry registry = Api102HookRegistry.processRegistry();
        if (registry.installedCount() != registry.unidentifiedCount()
                || !registry.releaseUnidentifiedForReload()
                || registry.installedCount() != 0) {
            Api101Bridge.log("[DC][API102] reload rejected: residual HookHandles");
            return false;
        }
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        // Never transfer old class-loader-owned Views, Hookers or listeners between generations.
        Api101Bridge.init(this);
        Object saved = param.getSavedInstanceState();
        if (("systemui-reload:" + SYSTEM_UI_PACKAGE).equals(saved)
                && SYSTEM_UI_PACKAGE.equals(param.getProcessName())) {
            loadedProcessName = SYSTEM_UI_PACKAGE;
            refreshDebugLogging();
            try {
                // Old owners have already detached before returning true. These handles are
                // idempotently unhooked as a defensive check, never transferred to new owners.
                for (io.github.libxposed.api.XposedInterface.HookHandle handle
                        : param.getOldHookHandles()) {
                    handle.unhook();
                }
                HookUtil.InvocationResult<Object> application =
                        HookUtil.tryInvokeActivityThreadCurrentApplication();
                if (!application.succeeded()
                        || !(application.value() instanceof android.content.Context)) {
                    throw new IllegalStateException("SystemUI application unavailable on reload");
                }
                if (!reloadGate.enterOwnerLifecycle()) {
                    throw new IllegalStateException("new generation owner gate closed");
                }
                installSystemUi(((android.content.Context) application.value()).getClassLoader());
                Api101Bridge.log("[DC][API102] SystemUI integration bootstrap attempted in new generation");
            } catch (Throwable error) {
                Api101Bridge.errorAlways("[DC][API102] SystemUI reload bootstrap failed", error);
            }
            return;
        }
        loadedProcessName = saved instanceof String ? (String) saved : null;
        refreshDebugLogging();
        Api101Bridge.log("[DC][API102] restored uninitialized process generation");
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
