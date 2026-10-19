package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.hellovoid.prismal.PrismalEdgeDecoration;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

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
    private static boolean producerUpdatesPolicyEnabled = true;
    private static boolean homeProducerOverride;
    private static long homeFreshnessSerial;
    private static ClipOwner materialClipOwner;
    private static ClipOwner parentClipOwner;
    private static final ArrayList<ClipOwner> taskbarAncestorClips = new ArrayList<>();
    private static View taskbarClipRoot;
    private static volatile boolean workstationTaskbarMode;
    private static final int[] materialScreenPosition = new int[2];
    private static final Point realScreenSize = new Point();

    private static final class ClipOwner {
        final ViewGroup view;
        final boolean clipChildren;
        final boolean clipPadding;

        ClipOwner(ViewGroup view) {
            this.view = view;
            clipChildren = view.getClipChildren();
            clipPadding = view.getClipToPadding();
            view.setClipChildren(false);
            view.setClipToPadding(false);
        }

        void restore() {
            view.setClipChildren(clipChildren);
            view.setClipToPadding(clipPadding);
        }
    }

    private Miuix307ZeroCopyRenderer() {}

    static boolean install(ViewGroup materialHost, DockLiquidGlassHostView host,
                           LiquidDockConfig.Glass glassConfig,
                           LiquidDockConfig.Workstation workstationConfig,
                           int blurRadiusPx) {
        if (materialHost == null || host == null || glassConfig == null
                || workstationConfig == null) return false;

        LiquidDockConfig.Glass liveGlass = LiveGlassConfigState.currentGlass();
        if (liveGlass != null) glassConfig = liveGlass;

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
        workstationTaskbarMode = WorkstationRuntimeState.isActive()
                && workstationConfig.dockEnabled && workstationConfig.taskbarMode;
        gpuBackdrop.setTaskbarMode(workstationTaskbarMode);
        gpuBackdrop.setWorkstationDockIconCornerRadiusDp(
                workstationConfig.dockIconGlassCornerRadius);
        gpuBackdrop.setId(View.generateViewId());

        // Prismal optics are evaluated in Dock-local UV space over the zero-copy OES backdrop.
        // The shell's safe foreground stroke may remain above the TextureView because it does not
        // alter producer geometry or backdrop sampling.
        host.removeAllViews();
        host.addView(gpuBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The original Dock host keeps its exact bounds. Only the texture
        // child expands into negative margins to expose external SDF shadows.
        // A geometry observer will re-evaluate these margins after layout.

        gpuBackdropRef = new WeakReference<>(gpuBackdrop);
        hostRef = new WeakReference<>(host);
        materialHostRef = new WeakReference<>(materialHost);
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        // Do not toggle native producer updates on folder re-parenting.
        // Its original vendor snapshot policy still owns the SF producer.
        refreshDockShadowViewport();
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

    static boolean isWorkstationTaskbarMode() {
        return workstationTaskbarMode;
    }

    static void setWorkstationTaskbarMode(boolean enabled) {
        if (workstationTaskbarMode == enabled) return;
        workstationTaskbarMode = enabled;
        Miuix307PassBlurTextureView output = gpuBackdropRef.get();
        if (output != null) output.setTaskbarMode(enabled);
        refreshDockShadowViewport();
        MiuixGlassHook.refreshTaskbarGeometry();
        requestDockSceneRefresh();
    }

    static void setNativeOpticsRadiusPx(float radiusPx) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.setNativeOpticsRadiusPx(radiusPx);
    }

    /**
     * Runs on the Launcher UI thread, only updates layout if the effective
     * shadow guard changed. Does NOT rebind SurfaceFlinger/PassBlur.
     */
    static void refreshDockShadowViewport() {
        Miuix307PassBlurTextureView output = gpuBackdropRef.get();
        DockLiquidGlassHostView host = hostRef.get();
        View material = materialHostRef.get();
        if (output == null || host == null || material == null
                || output.getParent() != host) return;
        PrismalEdgeDecoration style = GlassEdgeEffects.current(GlassEdgeEffects.DOCK);
        float density = host.getResources().getDisplayMetrics().density;
        int shortSide = Math.min(host.getWidth(), host.getHeight());
        int margin = DockPrismalShadowViewportPolicy.marginPx(style, density, shortSide);
        int leftMargin = margin, rightMargin = margin, bottomMargin = margin;
        if (workstationTaskbarMode) {
            Display display = material.getDisplay();
            if (display != null && material.getWidth() > 0 && material.getHeight() > 0) {
                display.getRealSize(realScreenSize);
                material.getLocationOnScreen(materialScreenPosition);
                int bleed = WorkstationTaskbarGeometry.bleedPx(density);
                leftMargin = WorkstationTaskbarGeometry.leftOutputMargin(
                        materialScreenPosition[0], bleed, margin);
                rightMargin = WorkstationTaskbarGeometry.rightOutputMargin(
                        materialScreenPosition[0], material.getWidth(),
                        realScreenSize.x, bleed, margin);
                bottomMargin = WorkstationTaskbarGeometry.bottomOutputMargin(
                        materialScreenPosition[1], material.getHeight(),
                        realScreenSize.y, bleed, margin);
            }
        }
        refreshTaskbarAncestorClips(material);
        if (!(output.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) output.getLayoutParams();
        if (lp.leftMargin == -leftMargin && lp.topMargin == -margin
                && lp.rightMargin == -rightMargin && lp.bottomMargin == -bottomMargin) return;

        if (leftMargin > 0 || margin > 0 || rightMargin > 0 || bottomMargin > 0) {
            if (material instanceof ViewGroup && materialClipOwner == null) {
                materialClipOwner = new ClipOwner((ViewGroup) material);
            }
            if (material.getParent() instanceof ViewGroup && parentClipOwner == null) {
                parentClipOwner = new ClipOwner((ViewGroup) material.getParent());
            }
        } else {
            restoreShadowClips();
        }

        lp.setMargins(-leftMargin, -margin, -rightMargin, -bottomMargin);
        output.setLayoutParams(lp);
        MainHook.log(TAG + " Prismal Dock viewport margins L/T/R/B="
                + leftMargin + "/" + margin + "/" + rightMargin + "/"
                + bottomMargin + "px taskbar=" + workstationTaskbarMode);
    }

    private static void refreshTaskbarAncestorClips(View material) {
        View root = material.getRootView();
        if (!workstationTaskbarMode || taskbarClipRoot != root) {
            restoreTaskbarAncestorClips();
        }
        if (!workstationTaskbarMode || !taskbarAncestorClips.isEmpty()) return;
        taskbarClipRoot = root;
        // TextureView output may span the screen while the vendor material and native icons
        // retain their original Dock bounds. Release only ancestor child-clips and restore
        // every original value when the option is disabled or the host detaches.
        ViewParent ancestor = material.getParent();
        if (ancestor instanceof View) ancestor = ((View) ancestor).getParent();
        while (ancestor instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) ancestor;
            taskbarAncestorClips.add(new ClipOwner(group));
            ancestor = group.getParent();
        }
    }

    private static void restoreTaskbarAncestorClips() {
        for (int i = taskbarAncestorClips.size() - 1; i >= 0; i--) {
            taskbarAncestorClips.get(i).restore();
        }
        taskbarAncestorClips.clear();
        taskbarClipRoot = null;
    }

    private static void restoreShadowClips() {
        restoreTaskbarAncestorClips();
        if (parentClipOwner != null) {
            parentClipOwner.restore();
            parentClipOwner = null;
        }
        if (materialClipOwner != null) {
            materialClipOwner.restore();
            materialClipOwner = null;
        }
    }

    static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx) {
        LiquidDockConfig.Glass live = LiveGlassConfigState.currentGlass();
        if (live != null) glassConfig = live;
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null && glassConfig != null) {
            gpuBackdrop.setGlassConfig(glassConfig);
            refreshDockShadowViewport();
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

    /** Resolve the Dock's REAL source root, including a binding that may be
     * replaced while the OEM moves the floating Dock. Used by OPEN_FOLDER
     * only; this never changes the Dock producer or its freeze/thaw policy.
     */
    static String[] folderCaptureDockSurfaceNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        Miuix307PassBlurTextureView output = gpuBackdropRef.get();
        if (output != null) {
            String bound = output.boundDockRootSurfaceName();
            if (bound != null && !bound.isBlank()) names.add(bound);
        }
        View material = materialHostRef.get();
        if (material != null && material.isAttachedToWindow()) {
            RootPassBlurEndpointBridge.Endpoint endpoint = RootPassBlurEndpointBridge.inspect(material);
            if (endpoint != null && endpoint.isValid()) {
                String current = Miuix307PassBlurBridge.surfaceName(endpoint.rootSurface);
                if (current != null && !current.isBlank()) names.add(current);
            }
        }
        return names.toArray(new String[0]);
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

        final long inputGenerationBaseline = gpuBackdrop.producerCallbackGeneration();
        homeProducerOverride = true;
        applyProducerUpdatesPolicy("home-refresh-start");
        awaitFreshHomeOutput(gpuBackdrop, serial, inputGenerationBaseline, 0);
        MainHook.log(TAG + " HOME backdrop refresh armed serial=" + serial
                + " inputGenerationBaseline=" + inputGenerationBaseline);
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
            long inputGenerationBaseline,
            int attempt) {
        if (gpuBackdropRef.get() != gpuBackdrop || serial != homeFreshnessSerial
                || !gpuBackdrop.isAttachedToWindow()) {
            return;
        }

        long presentedGeneration = gpuBackdrop.presentedProducerGeneration();
        if (DockHomeBackdropFreshnessState.isNewProducerFramePresented(
                inputGenerationBaseline, presentedGeneration)) {
            DockHomeBackdropFreshnessState.Decision decision =
                    HOME_FRESHNESS.onProducerFrameAvailable();
            if (decision.releaseProducerOverride) {
                homeProducerOverride = false;
                applyProducerUpdatesPolicy("home-refresh-frame-arrived");
                MainHook.log(TAG + " HOME backdrop refreshed serial=" + serial
                        + " presentedProducerGeneration=" + presentedGeneration);
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
                gpuBackdrop, serial, inputGenerationBaseline, attempt + 1));
    }

    static void requestDockSceneRefresh() {
        refreshDockShadowViewport();
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) {
            gpuBackdrop.requestDockSceneRefresh();
        }
    }

    static void requestDockProgressRefresh() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.requestDockProgressRefresh();
    }

    static void clear() {
        restoreShadowClips();
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        gpuBackdropRef = new WeakReference<>(null);
        hostRef = new WeakReference<>(null);
        materialHostRef = new WeakReference<>(null);
        producerUpdatesPolicyEnabled = true;
        homeProducerOverride = false;
        homeFreshnessSerial = 0L;
        HOME_FRESHNESS.reset();
        if (gpuBackdrop != null) gpuBackdrop.shutdown();
    }
}
