package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Builds the feedback-safe HyperOS 307 PassBlur -> OES -> TextureView material composition. */
final class Miuix307ZeroCopyRenderer {
    private static final String TAG = "[DC][ZC]";
    private static final float WORLD_LOCK_GUARD_DP = 64f;

    private static WeakReference<Miuix307PassBlurTextureView> gpuBackdropRef =
            new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef =
            new WeakReference<>(null);
    private static WeakReference<View> materialHostRef = new WeakReference<>(null);
    private static final DockHomeBackdropFreshnessState HOME_FRESHNESS =
            new DockHomeBackdropFreshnessState();
    private static final DockBackdropMotionSyncState MOTION_SYNC =
            new DockBackdropMotionSyncState();
    private static Method backdropMappingMethod;
    private static boolean producerUpdatesPolicyEnabled = true;
    private static boolean homeProducerOverride;
    private static long homeFreshnessSerial;
    private static boolean worldLockConfigured;
    private static boolean worldLockAnchored;
    private static int worldLockGuardPx;
    private static int worldLockAnchorX;
    private static int worldLockAnchorY;

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

        // The TextureView is expanded after Host measurement. Its extra pixels are real sampled
        // world-space background, not a scaled copy. During motion we counter-translate this child
        // so its screen position stays fixed while the Host clip follows the Dock in the UI
        // timeline. This removes BufferQueue/EGL presentation latency from backdrop positioning.
        host.removeAllViews();
        host.addView(gpuBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gpuBackdropRef = new WeakReference<>(gpuBackdrop);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);
        MOTION_SYNC.reset();
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        worldLockConfigured = false;
        worldLockAnchored = false;
        worldLockGuardPx = 0;
        worldLockAnchorX = 0;
        worldLockAnchorY = 0;
        host.post(() -> configureWorldLockOverscan(gpuBackdrop, host));
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
        DockLiquidGlassHostView host = hostRef.get();
        if (host != null && host.getWidth() > 0) return host.getWidth();
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        return gpuBackdrop != null ? gpuBackdrop.getWidth() : 0;
    }

    static int activeHeight() {
        DockLiquidGlassHostView host = hostRef.get();
        if (host != null && host.getHeight() > 0) return host.getHeight();
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
     * the App buffer as soon as SurfaceFlinger produces it. No alpha/visibility barrier is used.
     */
    static void onHomeOpeningStarted() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;

        final long serial = ++homeFreshnessSerial;
        DockHomeBackdropFreshnessState.Decision decision = HOME_FRESHNESS.onHomeStarted(serial);
        if (!decision.forceProducerUpdates) return;

        final long inputTimestampBaseline = readInputTimestamp(gpuBackdrop);
        homeProducerOverride = true;
        applyProducerUpdatesPolicy("home-refresh-start");
        awaitFreshHomeInput(gpuBackdrop, serial, inputTimestampBaseline);
        MainHook.log(TAG + " HOME backdrop refresh armed serial=" + serial
                + " inputTimestampBaseline=" + inputTimestampBaseline);
    }

    /** HOME FINISH never hides the Dock; refresh was already armed at HOME START. */
    static void onHomeOpeningFinished() {
        if (homeFreshnessSerial <= 0L) return;
        HOME_FRESHNESS.onHomeFinished(homeFreshnessSerial);
        MainHook.log(TAG + " HOME finish observed without presentation barrier serial="
                + homeFreshnessSerial);
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
            DockHomeBackdropFreshnessState.Decision decision =
                    HOME_FRESHNESS.onProducerFrameAvailable();
            if (decision.releaseProducerOverride) {
                homeProducerOverride = false;
                applyProducerUpdatesPolicy("home-refresh-frame-arrived");
                MainHook.log(TAG + " HOME backdrop refreshed serial=" + serial
                        + " inputTimestamp=" + inputTimestamp);
            }
            return;
        }

        gpuBackdrop.postOnAnimation(() -> awaitFreshHomeInput(
                gpuBackdrop, serial, inputTimestampBaseline));
    }

    private static long readInputTimestamp(Miuix307PassBlurTextureView gpuBackdrop) {
        try {
            Object value = HookUtil.getField(gpuBackdrop, "inputSurfaceTexture");
            return value instanceof SurfaceTexture ? ((SurfaceTexture) value).getTimestamp() : 0L;
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME refresh input timestamp unavailable: " + error);
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

    /**
     * FloatingIcon update arrives after the vendor has applied this frame's animation state. Keep
     * the oversized backdrop child fixed in world coordinates first, then refresh mapping. The
     * moving Host clip is therefore synchronized with Launcher while the background buffer is not
     * required to traverse EGL/BufferQueue on every position change.
     */
    static void requestDockAnimationFrames() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;

        applyWorldLockForMotion(gpuBackdrop);
        DockBackdropMotionSyncState.Decision decision = MOTION_SYNC.onVendorMotionFrame();
        if (decision.refreshMappingNow) {
            syncBackdropMappingForMotion(gpuBackdrop);
        }
        if (!decision.scheduleContinuation) return;

        DockAnimationTrace.rendererEvent("anim-frame-request");
        gpuBackdrop.requestDockSceneRefresh();
        gpuBackdrop.postOnAnimation(() -> {
            if (gpuBackdropRef.get() != gpuBackdrop) return;
            DockAnimationTrace.rendererEvent("anim-frame-vsync");
            MOTION_SYNC.onContinuationVsync();
            if (DockGlassItemRegistry.hasActiveAnimation()) {
                requestDockAnimationFrames();
            }
        });
    }

    private static void configureWorldLockOverscan(
            Miuix307PassBlurTextureView gpuBackdrop,
            DockLiquidGlassHostView host) {
        if (gpuBackdropRef.get() != gpuBackdrop || hostRef.get() != host
                || !host.isAttachedToWindow()) return;
        int hostWidth = host.getWidth();
        int hostHeight = host.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0) {
            host.postOnAnimation(() -> configureWorldLockOverscan(gpuBackdrop, host));
            return;
        }
        int guard = Math.max(1, Math.round(WORLD_LOCK_GUARD_DP
                * host.getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                hostWidth + guard * 2,
                hostHeight + guard * 2);
        lp.leftMargin = -guard;
        lp.topMargin = -guard;
        gpuBackdrop.setLayoutParams(lp);
        gpuBackdrop.setTranslationX(0f);
        gpuBackdrop.setTranslationY(0f);
        worldLockGuardPx = guard;
        worldLockConfigured = true;
        worldLockAnchored = false;
        MainHook.log(TAG + " Dock world-lock overscan configured host="
                + hostWidth + "x" + hostHeight + " guard=" + guard);
    }

    private static void applyWorldLockForMotion(Miuix307PassBlurTextureView gpuBackdrop) {
        DockLiquidGlassHostView host = hostRef.get();
        if (host == null || !host.isAttachedToWindow()) return;
        if (!worldLockConfigured || worldLockGuardPx <= 0
                || gpuBackdrop.getWidth() <= host.getWidth()
                || gpuBackdrop.getHeight() <= host.getHeight()) {
            configureWorldLockOverscan(gpuBackdrop, host);
            if (!worldLockConfigured) return;
        }

        int[] hostScreen = new int[2];
        host.getLocationOnScreen(hostScreen);
        if (!worldLockAnchored) {
            worldLockAnchorX = hostScreen[0];
            worldLockAnchorY = hostScreen[1];
            worldLockAnchored = true;
        }

        int guard = worldLockGuardPx;
        DockBackdropWorldLockState.Crop crop = DockBackdropWorldLockState.compute(
                host.getWidth(), host.getHeight(),
                host.getWidth() + guard * 2, host.getHeight() + guard * 2,
                guard, guard,
                worldLockAnchorX, worldLockAnchorY,
                hostScreen[0], hostScreen[1]);
        float translationX = -(crop.sourceLeft - guard);
        float translationY = -(crop.sourceTop - guard);
        if (Float.compare(gpuBackdrop.getTranslationX(), translationX) != 0) {
            gpuBackdrop.setTranslationX(translationX);
        }
        if (Float.compare(gpuBackdrop.getTranslationY(), translationY) != 0) {
            gpuBackdrop.setTranslationY(translationY);
        }
    }

    private static void syncBackdropMappingForMotion(Miuix307PassBlurTextureView gpuBackdrop) {
        try {
            Method method = backdropMappingMethod;
            if (method == null) {
                method = HookUtil.findMethodExact(
                        Miuix307PassBlurTextureView.class,
                        "updateBackdropMapping",
                        new Class<?>[0]);
                backdropMappingMethod = method;
            }
            method.invoke(gpuBackdrop);
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock motion mapping sync unavailable: " + error);
        }
    }

    static void clear() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        gpuBackdropRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        MOTION_SYNC.reset();
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        worldLockConfigured = false;
        worldLockAnchored = false;
        worldLockGuardPx = 0;
        worldLockAnchorX = 0;
        worldLockAnchorY = 0;
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
