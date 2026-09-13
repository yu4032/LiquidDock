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
    private static int worldLockHostWidth;
    private static int worldLockHostHeight;
    private static int worldLockAnchorX;
    private static int worldLockAnchorY;

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig,
                           LiquidDockConfig.Workstation workstationConfig,
                           int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null
                || workstationConfig == null) return false;

        LiquidDockConfig runtimeConfig = LiquidDockConfig.load();
        boolean animationHookInstalled = DockIconAnimationGlassHook.install(
                materialHost.getClass().getClassLoader(), runtimeConfig);
        MainHook.log(TAG + " Dock icon animation hook installed=" + animationHookInstalled
                + " iconEnabled=" + GlassRuntimeState.isIconEnabled()
                + " host=" + materialHost.getClass().getSimpleName());

        Miuix307PassBlurTextureView gpuBackdrop = new Miuix307PassBlurTextureView(
                materialHost.getContext(), materialHost);
        gpuBackdrop.setGlassConfig(glassConfig);
        gpuBackdrop.setWorkstationDockIconCornerRadiusDp(
                workstationConfig.dockIconGlassCornerRadius);
        gpuBackdrop.setId(View.generateViewId());

        // Give the TextureView a real overscan ring. The parent Host continues to own the exact
        // Dock clip/stroke. During motion the oversized child is counter-translated on the UI
        // timeline so its already-presented pixels stay fixed in screen/world coordinates.
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
        resetWorldLockState();
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

        int[] hostScreen = new int[2];
        host.getLocationOnScreen(hostScreen);
        worldLockGuardPx = guard;
        worldLockHostWidth = hostWidth;
        worldLockHostHeight = hostHeight;
        worldLockAnchorX = hostScreen[0];
        worldLockAnchorY = hostScreen[1];
        worldLockConfigured = true;
        worldLockAnchored = true;
        MainHook.log(TAG + " Dock world-lock overscan configured host="
                + hostWidth + "x" + hostHeight + " guard=" + guard
                + " anchor=[" + worldLockAnchorX + "," + worldLockAnchorY + "]");
    }

    private static void applyWorldLockForMotion(Miuix307PassBlurTextureView gpuBackdrop) {
        DockLiquidGlassHostView host = hostRef.get();
        if (host == null || !host.isAttachedToWindow()) return;

        int hostWidth = host.getWidth();
        int hostHeight = host.getHeight();
        if (!worldLockConfigured || worldLockGuardPx <= 0
                || hostWidth != worldLockHostWidth || hostHeight != worldLockHostHeight) {
            configureWorldLockOverscan(gpuBackdrop, host);
        }
        if (!worldLockConfigured || !worldLockAnchored) return;

        // setLayoutParams() is asynchronous. Never re-anchor merely because the enlarged child has
        // not completed layout yet; wait for the overscan dimensions to become real instead.
        int guard = worldLockGuardPx;
        int expectedWidth = hostWidth + guard * 2;
        int expectedHeight = hostHeight + guard * 2;
        if (gpuBackdrop.getWidth() != expectedWidth || gpuBackdrop.getHeight() != expectedHeight) {
            return;
        }

        int[] hostScreen = new int[2];
        host.getLocationOnScreen(hostScreen);
        DockBackdropWorldLockState.Crop crop = DockBackdropWorldLockState.compute(
                hostWidth, hostHeight,
                expectedWidth, expectedHeight,
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

    private static void resetWorldLockState() {
        worldLockConfigured = false;
        worldLockAnchored = false;
        worldLockGuardPx = 0;
        worldLockHostWidth = 0;
        worldLockHostHeight = 0;
        worldLockAnchorX = 0;
        worldLockAnchorY = 0;
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
        resetWorldLockState();
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
