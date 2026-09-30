package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Builds the feedback-safe HyperOS 307 PassBlur -> OES -> TextureView material composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";
    private static final int MAX_HOME_FRESH_WAIT_FRAMES = 120;

    private static WeakReference<Miuix307PassBlurTextureView> gpuBackdropRef =
            new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef =
            new WeakReference<>(null);
    private static WeakReference<View> materialHostRef = new WeakReference<>(null);
    private static final DockHomeBackdropFreshnessState HOME_FRESHNESS =
            new DockHomeBackdropFreshnessState();
    private static boolean dockAnimationFrameScheduled;
    private static boolean producerUpdatesPolicyEnabled = true;
    private static boolean homeProducerOverride;
    private static long homeFreshnessSerial;

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig,
                           LiquidDockConfig.Workstation workstationConfig,
                           int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null
                || workstationConfig == null) return false;

        // This is the first zero-copy boundary that owns a real Launcher View. Install the
        // app-to-home icon handoff hooks here so they use the target Launcher ClassLoader rather
        // than being skipped by MainHook's successful 307 early return.
        LiquidDockConfig runtimeConfig = LiquidDockConfig.load();
        boolean animationHookInstalled = DockIconAnimationGlassHook.install(
                materialHost.getClass().getClassLoader(), runtimeConfig);
        MainHook.log(TAG + " Dock icon animation hook installed=" + animationHookInstalled
                + " iconEnabled=" + GlassRuntimeState.isIconEnabled()
                + " host=" + materialHost.getClass().getSimpleName());

        // The current zero-copy backend binds SurfaceFlinger's PassBlur producer directly to the
        // Floating Dock root through SetPassBlurSurface. It does not depend on the themed
        // BlurBackground2#setBackgroundBlur path, so both supported HotSeats material owners must
        // reach the same TextureView renderer.
        Miuix307PassBlurTextureView gpuBackdrop = new Miuix307PassBlurTextureView(
                materialHost.getContext(), materialHost);
        gpuBackdrop.setGlassConfig(glassConfig);
        gpuBackdrop.setWorkstationDockIconCornerRadiusDp(
                workstationConfig.dockIconGlassCornerRadius);
        gpuBackdrop.setId(View.generateViewId());

        // Prismal optics are evaluated in Dock-local UV space over the zero-copy OES backdrop.
        // The shell's safe foreground stroke may remain above the TextureView because it does not
        // alter producer geometry or backdrop sampling.
        host.removeAllViews();
        host.addView(gpuBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gpuBackdropRef = new WeakReference<>(gpuBackdrop);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);
        dockAnimationFrameScheduled = false;
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        MainHook.log(TAG + " PassBlur TextureView EGL Prismal material installed; awaiting first GPU frame"
                + " requestedBlur=" + blurRadiusPx
                + " source=" + materialHost.getClass().getSimpleName());
        return true;
    }

    static boolean isInstalled() {
        return gpuBackdropRef.get() != null;
    }

    static boolean isActive() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null && gpuBackdrop.isGpuBackdropActive();
    }

    static boolean isActivationExhausted() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop == null || gpuBackdrop.isActivationExhausted();
    }

    static int activeWidth() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getWidth() : 0;
    }

    static int activeHeight() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getHeight() : 0;
    }

    static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null && glassConfig != null) {
            gpuBackdrop.setGlassConfig(glassConfig);
        }
    }

    static void rebindProducer(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.rebindProducer(reason);
    }

    static void setProducerUpdatesEnabled(boolean enabled, String reason) {
        producerUpdatesPolicyEnabled = enabled;
        applyProducerUpdatesPolicy(reason);
    }

    private static void applyProducerUpdatesPolicy(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;
        boolean effective = producerUpdatesPolicyEnabled || homeProducerOverride;
        gpuBackdrop.setProducerUpdatesEnabled(effective, reason
                + (homeProducerOverride ? "/home-refresh-override" : ""));
    }

    /**
     * HOME authority starts the refresh immediately. Keep the already-presented Dock visible, but
     * temporarily force the PassBlur producer live so the first desktop/wallpaper buffer replaces
     * the App buffer as soon as the TextureView presents a new frame. No alpha/visibility barrier
     * is used.
     */
    static void onHomeOpeningStarted() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;

        final long serial = ++homeFreshnessSerial;
        DockHomeBackdropFreshnessState.Decision decision = HOME_FRESHNESS.onHomeStarted(serial);
        if (!decision.forceProducerUpdates) return;

        final long outputTimestampBaseline = readOutputTimestamp(gpuBackdrop);
        homeProducerOverride = true;
        applyProducerUpdatesPolicy("home-refresh-start");
        awaitFreshHomeOutput(gpuBackdrop, serial, outputTimestampBaseline, 0);
        MainHook.log(TAG + " HOME backdrop refresh armed serial=" + serial
                + " outputTimestampBaseline=" + outputTimestampBaseline);
    }

    /** HOME FINISH never hides the Dock; refresh was already armed at HOME START. */
    static void onHomeOpeningFinished() {
        if (homeFreshnessSerial <= 0L) return;
        HOME_FRESHNESS.onHomeFinished(homeFreshnessSerial);
        MainHook.log(TAG + " HOME finish observed without presentation barrier serial="
                + homeFreshnessSerial);
    }

    private static void awaitFreshHomeOutput(
            Miuix307PassBlurTextureView gpuBackdrop,
            long serial,
            long outputTimestampBaseline,
            int attempt) {
        if (gpuBackdropRef.get() != gpuBackdrop || serial != homeFreshnessSerial
                || !gpuBackdrop.isAttachedToWindow()) {
            return;
        }

        long outputTimestamp = readOutputTimestamp(gpuBackdrop);
        if (outputTimestamp > 0L && outputTimestamp != outputTimestampBaseline) {
            DockHomeBackdropFreshnessState.Decision decision =
                    HOME_FRESHNESS.onProducerFrameAvailable();
            if (decision.releaseProducerOverride) {
                homeProducerOverride = false;
                applyProducerUpdatesPolicy("home-refresh-frame-arrived");
                MainHook.log(TAG + " HOME backdrop refreshed serial=" + serial
                        + " outputTimestamp=" + outputTimestamp);
            }
            return;
        }

        if (attempt >= MAX_HOME_FRESH_WAIT_FRAMES) {
            HOME_FRESHNESS.reset();
            homeProducerOverride = false;
            applyProducerUpdatesPolicy("home-refresh-wait-exhausted");
            MainHook.log(TAG + " HOME backdrop refresh wait exhausted serial=" + serial);
            return;
        }

        gpuBackdrop.postOnAnimation(() -> awaitFreshHomeOutput(
                gpuBackdrop, serial, outputTimestampBaseline, attempt + 1));
    }

    private static long readOutputTimestamp(Miuix307PassBlurTextureView gpuBackdrop) {
        SurfaceTexture output = gpuBackdrop.getSurfaceTexture();
        return output != null ? output.getTimestamp() : 0L;
    }

    static void requestDockSceneRefresh() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) {
            DockAnimationTrace.rendererEvent("scene-refresh-request");
            gpuBackdrop.requestDockSceneRefresh();
        }
    }

    static void requestDockAnimationFrames() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null || dockAnimationFrameScheduled) return;
        dockAnimationFrameScheduled = true;
        DockAnimationTrace.rendererEvent("anim-frame-request");
        gpuBackdrop.requestDockSceneRefresh();
        gpuBackdrop.postOnAnimation(() -> {
            if (gpuBackdropRef.get() != gpuBackdrop) return;
            DockAnimationTrace.rendererEvent("anim-frame-vsync");
            dockAnimationFrameScheduled = false;
            if (DockGlassItemRegistry.hasActiveAnimation()) {
                requestDockAnimationFrames();
            }
        });
    }

    static void clear() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        gpuBackdropRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        dockAnimationFrameScheduled = false;
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
