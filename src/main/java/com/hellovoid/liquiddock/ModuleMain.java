package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigSchema;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point; hot reload remains disabled for owned process resources. */
public final class ModuleMain extends XposedModule {
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
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!SYSTEM_UI_PACKAGE.equals(param.getPackageName())) return;
        refreshDebugLogging();
        try {
            ConfigReader configReader = ConfigReader.load();
            boolean enabled = configReader.b(
                    ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback());
            boolean headerGradientBlurEnabled = configReader.b(
                    ConfigSchema.Animation.SYSTEM_UI_HEADER_GRADIENT_BLUR.name(), false);
            SystemUiHeaderGradientBlurHook.install(enabled && headerGradientBlurEnabled);
        } catch (Throwable error) {
            Api101Bridge.log(
                    "[DC][SystemUiHeaderGradientBlur] early bootstrap failed; preserving SystemUI behavior",
                    error);
        }
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        if (!reloadGate.enterOwnerLifecycle()) return;
        WallpaperNativeTransformBridge.installSystemObserver(param.getClassLoader());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        refreshDebugLogging();
        String packageName = param.getPackageName();
        // Claim ownership before any install operation, including failing/partial installs.
        // In an admitted pre-install handoff the old generation must not start new hooks.
        if ((SYSTEM_UI_PACKAGE.equals(packageName)
                || SecurityCenterProcessPolicy.PACKAGE.equals(packageName)
                || ThirdPartyGlassAdapterRegistry.handles(packageName))
                && !reloadGate.enterOwnerLifecycle()) {
            return;
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
                ExternalGlassLiveConfigState.initialize(packageName);
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
        // OS4 Launcher (com.miui.home) is handled exclusively by the Rust+Flutter native hook.
    }

    /**
     * API 102 can only transfer an UNINITIALIZED generation. Package-ready callbacks are not
     * replayed automatically, and owners of hooks, views, receivers and EGL cannot be detached.
     * The module remains non-auto-reloadable until every owner has a verified teardown path.
     */
    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
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

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        // Only the no-owners path above can succeed. Never move a HookHandle, View, or closure
        // across class loaders, and never try to replay onPackageReady manually.
        Api101Bridge.init(this);
        Object saved = param.getSavedInstanceState();
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
