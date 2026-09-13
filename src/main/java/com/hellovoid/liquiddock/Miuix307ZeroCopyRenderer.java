package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

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
    private static final DockBackdropMotionSyncState MOTION_SYNC =
            new DockBackdropMotionSyncState();
    private static Method backdropMappingMethod;
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

        // Keep the output View exactly Dock-sized. Motion corrections belong in mapping space;
        // resizing/moving this TextureView changes the EGL output geometry and Prismal coordinate
        // domain, which was the cause of the failed oversized world-lock experiment.
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
        Miuix307BackdropMapping.clearTransformOverride();
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

    /**
     * FloatingIcon update arrives after Launcher has applied the current transform. Capture the
     * complete View/ancestor matrix before publishing this mapping generation. getLocationOnScreen
     * alone is insufficient because the old path paired the transformed origin with untransformed
     * layout width/height, so scaled Dock frames sampled the wrong screen rectangle.
     */
    static void requestDockAnimationFrames() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop == null) return;

        updateBackdropTransformOverride(gpuBackdrop);
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
            } else {
                Miuix307BackdropMapping.clearTransformOverride();
                syncBackdropMappingForMotion(gpuBackdrop);
            }
        });
    }

    private static void updateBackdropTransformOverride(Miuix307PassBlurTextureView gpuBackdrop) {
        int width = gpuBackdrop.getWidth();
        int height = gpuBackdrop.getHeight();
        if (width <= 0 || height <= 0 || !gpuBackdrop.isAttachedToWindow()) {
            Miuix307BackdropMapping.clearTransformOverride();
            return;
        }
        try {
            int[] baseScreen = new int[2];
            gpuBackdrop.getLocationOnScreen(baseScreen);

            Matrix global = new Matrix();
            gpuBackdrop.transformMatrixToGlobal(global);
            float[] points = new float[]{
                    0f, 0f,
                    width, 0f,
                    0f, height,
                    width, height
            };
            global.mapPoints(points);

            // transformMatrixToGlobal() and mWinFrameInScreen can differ by the root/window origin
            // on vendor builds. Reconcile through the root rather than through the animated Dock,
            // preserving the Dock's actual transform while moving the coordinate system to screen.
            View root = gpuBackdrop.getRootView();
            float offsetX = 0f;
            float offsetY = 0f;
            if (root != null) {
                Matrix rootGlobal = new Matrix();
                root.transformMatrixToGlobal(rootGlobal);
                float[] rootOrigin = new float[]{0f, 0f};
                rootGlobal.mapPoints(rootOrigin);
                int[] rootScreen = new int[2];
                root.getLocationOnScreen(rootScreen);
                offsetX = rootScreen[0] - rootOrigin[0];
                offsetY = rootScreen[1] - rootOrigin[1];
            }
            for (int i = 0; i < points.length; i += 2) {
                points[i] += offsetX;
                points[i + 1] += offsetY;
            }

            DockBackdropTransformedGeometry.Bounds bounds =
                    DockBackdropTransformedGeometry.fromQuad(
                            points[0], points[1],
                            points[2], points[3],
                            points[4], points[5],
                            points[6], points[7],
                            width, height);
            Miuix307BackdropMapping.setTransformOverride(
                    baseScreen[0], baseScreen[1], width, height,
                    bounds.left, bounds.top, bounds.width, bounds.height);
        } catch (Throwable error) {
            Miuix307BackdropMapping.clearTransformOverride();
            MainHook.log(TAG + " transformed Dock geometry unavailable: " + error);
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
        Miuix307BackdropMapping.clearTransformOverride();
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
