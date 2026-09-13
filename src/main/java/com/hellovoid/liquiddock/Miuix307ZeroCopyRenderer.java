package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Builds the feedback-safe HyperOS 307 PassBlur -> OES -> TextureView material composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";

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
                + (homeProducerOverride ? "/home-freshness-override" : ""));
    }

    /** Mark the current producer content stale without disturbing the vendor return animation. */
    static void onHomeOpeningStarted() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;
        long serial = ++homeFreshnessSerial;
        DockHomeBackdropFreshnessState.Decision decision = HOME_FRESHNESS.onHomeStarted(serial);
        boolean hadOverride = homeProducerOverride;
        homeProducerOverride = false;
        if (hadOverride) applyProducerUpdatesPolicy("home-freshness-restarted");
        if (decision.blockPresentation) {
            MainHook.log(TAG + " HOME backdrop marked stale serial=" + serial);
        }
    }

    /**
     * The vendor may enter static-Dock snapshot mode at HOME and pause PassBlur updates. At the
     * accepted HOME FINISH boundary, stop exposing the stale App texture and temporarily override
     * that power policy until an input buffer newer than FINISH is consumed. Cross one UI VSYNC
     * before exposing the TextureView again, then restore the vendor-requested policy. No fixed
     * timing assumption is used.
     */
    static void onHomeOpeningFinished() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null || homeFreshnessSerial <= 0L) return;
        final long serial = homeFreshnessSerial;
        DockHomeBackdropFreshnessState.Decision decision = HOME_FRESHNESS.onHomeFinished(serial);
        if (!decision.forceProducerUpdates) return;

        final long inputTimestampBaseline = readInputTimestamp(gpuBackdrop);
        gpuBackdrop.setAlpha(0f);
        homeProducerOverride = true;
        applyProducerUpdatesPolicy("home-freshness-finish");
        awaitFreshHomeInput(gpuBackdrop, serial, inputTimestampBaseline);
        MainHook.log(TAG + " HOME backdrop fresh frame armed serial=" + serial
                + " inputTimestampBaseline=" + inputTimestampBaseline);
    }

    private static void awaitFreshHomeInput(
            Miuix307PassBlurTextureView gpuBackdrop,
            long serial,
            long inputTimestampBaseline) {
        if (gpuBackdropRef.get() != gpuBackdrop || serial != homeFreshnessSerial
                || !gpuBackdrop.isAttachedToWindow()) {
            return;
        }

        long inputTimestamp = readInputTimestamp(gpuBackdrop);
        if (inputTimestamp > 0L && inputTimestamp != inputTimestampBaseline) {
            // input.getTimestamp() changes only after drawLatestFrame() consumes updateTexImage().
            // Publish on the next UI VSYNC so that render-thread normalization/composition and the
            // corresponding EGL swap can complete before stale presentation is made visible again.
            gpuBackdrop.postOnAnimation(() -> publishFreshHomeBackdrop(
                    gpuBackdrop, serial, inputTimestamp));
            return;
        }

        gpuBackdrop.postOnAnimation(() -> awaitFreshHomeInput(
                gpuBackdrop, serial, inputTimestampBaseline));
    }

    private static void publishFreshHomeBackdrop(
            Miuix307PassBlurTextureView gpuBackdrop, long serial, long inputTimestamp) {
        if (gpuBackdropRef.get() != gpuBackdrop || serial != homeFreshnessSerial
                || !gpuBackdrop.isAttachedToWindow()) {
            return;
        }
        DockHomeBackdropFreshnessState.Decision decision =
                HOME_FRESHNESS.onProducerFrameAvailable();
        if (decision.releasePresentation) {
            gpuBackdrop.setAlpha(1f);
            MainHook.log(TAG + " HOME backdrop fresh frame published serial=" + serial
                    + " inputTimestamp=" + inputTimestamp);
        }
        if (decision.releaseProducerOverride) {
            homeProducerOverride = false;
            applyProducerUpdatesPolicy("home-freshness-complete");
        }
    }

    private static long readInputTimestamp(Miuix307PassBlurTextureView gpuBackdrop) {
        try {
            Object value = HookUtil.getField(gpuBackdrop, "inputSurfaceTexture");
            return value instanceof SurfaceTexture ? ((SurfaceTexture) value).getTimestamp() : 0L;
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME freshness input timestamp unavailable: " + error);
            return 0L;
        }
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
