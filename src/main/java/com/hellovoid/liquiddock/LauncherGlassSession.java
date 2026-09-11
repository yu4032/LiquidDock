package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Launcher-specific consumer of one root-wide {@link RootPassBlurBackend}.
 *
 * <p>Launcher keeps node registries, output ownership, Workspace projection, wallpaper authority,
 * Recents/Workstation policy and rotation settle. The native producer, OES input, normalization,
 * source freshness and EGL source lifecycle live only in RootPassBlurBackend.</p>
 */
final class LauncherGlassSession implements RootPassBlurBackend.Consumer {
    private static final String TAG = "[DC][LauncherGlass]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
    private static final AtomicInteger NEXT_SESSION_ID = new AtomicInteger(1);
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class WallpaperFrameToken {
        static final WallpaperFrameToken NONE = new WallpaperFrameToken(-1L, false);
        final long generation;
        final boolean authoritative;

        WallpaperFrameToken(long generation, boolean authoritative) {
            this.generation = generation;
            this.authoritative = authoritative;
        }
    }

    private static final class NodeState {
        final WeakReference<LauncherGlassSinkView> sinkRef;
        volatile LauncherGlassGeometry.Snapshot geometry;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        NodeState(LauncherGlassSinkView sink) {
            sinkRef = new WeakReference<>(sink);
        }
    }

    private static final class StaticGeometryFrame {
        final LauncherGlassGeometry.Snapshot geometry;
        final int workspaceScrollX;
        final boolean workspaceScrollValid;

        StaticGeometryFrame(
                LauncherGlassGeometry.Snapshot geometry,
                int workspaceScrollX,
                boolean workspaceScrollValid) {
            this.geometry = geometry;
            this.workspaceScrollX = workspaceScrollX;
            this.workspaceScrollValid = workspaceScrollValid;
        }
    }

    private static final class StaticNodeState {
        final WeakReference<LauncherGlassStaticNode> nodeRef;
        volatile StaticGeometryFrame frame;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        StaticNodeState(LauncherGlassStaticNode node) {
            nodeRef = new WeakReference<>(node);
        }
    }

    private static final class OutputState {
        final Surface surface;
        EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        int width;
        int height;

        OutputState(Surface surface, int width, int height) {
            this.surface = surface;
            this.width = width;
            this.height = height;
        }
    }

    private final int sessionId = NEXT_SESSION_ID.getAndIncrement();
    private final WeakReference<View> rootRef;
    private final Handler mainHandler;
    private final FloatBuffer quadBuffer;
    private final RootPassBlurBackend sourceBackend;
    private final LauncherGlassScrollProjectionState workspaceScrollProjection =
            new LauncherGlassScrollProjectionState();
    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LauncherGlassStaticNode, StaticNodeState> staticNodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    // Render-thread only. EGL surfaces are created through sourceBackend's shared EGL context.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();
    private final Object outputWorkLock = new Object();

    private volatile boolean shuttingDown;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile launcherHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile PrismalHighlightProfile largeSurfaceHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile int rootWidth;
    private volatile int rootHeight;
    private volatile int configRotation;
    private volatile long sceneGeneration = 1L;
    private volatile int passBlurCaptureScalePercent =
            PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
    private volatile int passBlurRenderFps = PassBlurQualityPolicy.DEFAULT_RENDER_FPS;

    // Launcher-only wallpaper authority. A source frame consumes the token only after Prismal render.
    private long wallpaperRequestedGeneration = -1L;
    private long wallpaperRequestedSceneGeneration = -1L;
    private boolean wallpaperRequestedAuthoritative;

    // Launcher-specific Shell rotation settle policy intentionally stays above the root backend.
    private volatile long rotationSettleSerial;
    private volatile boolean rotationSettlePending;
    private volatile int rotationSettleTargetRotation = -1;

    // Render-thread only Launcher output objects.
    private OutputState staticOutput;
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private boolean backdropPrepared;
    private boolean pendingStaticRender;
    private boolean pendingDragRender;
    private boolean outputRenderQueued;

    private ViewTreeObserver rootObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private final View.OnAttachStateChangeListener rootAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    installRootObserver();
                }

                @Override public void onViewDetachedFromWindow(View v) {
                    mainHandler.post(() -> {
                        View root = rootRef.get();
                        if (root == v && !v.isAttachedToWindow()) shutdown();
                    });
                }
            };

    LauncherGlassSession(View root, LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        rootWidth = Math.max(0, root.getWidth());
        rootHeight = Math.max(0, root.getHeight());
        configRotation = readLauncherConfigRotation(root);
        mainHandler = new Handler(root.getContext().getMainLooper());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
        applyGlassConfig(glassConfig);
        sourceBackend = new RootPassBlurBackend(
                root,
                PassBlurBindRequest.launcherWorkspace(root, 1.0f),
                passBlurCaptureScalePercent,
                passBlurRenderFps,
                this,
                "LiquidDock-LauncherGlass-EGL");
        root.addOnAttachStateChangeListener(rootAttachListener);
        installRootObserver();
        MainHook.log(TAG + " " + debugLabel()
                + " created source=RootPassBlurBackend domain=LAUNCHER_WORKSPACE");
    }

    boolean isShutdown() {
        return shuttingDown;
    }

    String diagnosticSessionId() {
        return "session#" + sessionId;
    }

    boolean ownsRoot(View root) {
        return root != null && rootRef.get() == root;
    }

    String debugLabel() {
        View root = rootRef.get();
        String name = root != null ? root.getClass().getSimpleName() : "released-root";
        int width = root != null ? root.getWidth() : rootWidth;
        int height = root != null ? root.getHeight() : rootHeight;
        return "session#" + sessionId + " root=" + name + "@"
                + Integer.toHexString(root != null ? System.identityHashCode(root) : 0)
                + " size=" + width + "x" + height;
    }

    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {
        if (shuttingDown) return;
        applyGlassConfig(glassConfig);
        sourceBackend.setQuality(passBlurCaptureScalePercent, passBlurRenderFps);
        mainHandler.post(() -> {
            if (shuttingDown) return;
            syncSceneOnUiThread();
            View root = rootRef.get();
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
        });
    }

    private void applyGlassConfig(LiquidDockConfig.Glass glassConfig) {
        View root = rootRef.get();
        float density = root != null ? root.getResources().getDisplayMetrics().density : 1f;
        passBlurCaptureScalePercent = glassConfig != null
                ? glassConfig.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        passBlurRenderFps = glassConfig != null
                ? glassConfig.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        prismalParams = Miuix307PrismalAdapter.toPortable(optical);
        launcherHighlightProfile = glassConfig != null
                ? glassConfig.launcherHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        largeSurfaceHighlightProfile = glassConfig != null
                ? glassConfig.largeSurfaceHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
    }

    void registerSink(LauncherGlassSinkView sink) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            if (!nodes.containsKey(sink)) nodes.put(sink, new NodeState(sink));
        }
        syncSceneOnUiThread();
        requestDragRedraw();
    }

    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestDragRedraw();
    }

    void updateInteraction(LauncherGlassSinkView sink, PrismalInteractionState interaction) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            NodeState node = nodes.get(sink);
            if (node == null) return;
            node.interaction = interaction != null ? interaction : PrismalInteractionState.IDLE;
        }
        requestDragRedraw();
    }

    void registerStaticNode(LauncherGlassStaticNode node) {
        if (node == null || shuttingDown) return;
        synchronized (staticNodes) {
            if (!staticNodes.containsKey(node)) staticNodes.put(node, new StaticNodeState(node));
        }
        syncSceneOnUiThread();
        requestStaticRedraw();
    }

    void unregisterStaticNode(LauncherGlassStaticNode node) {
        if (node == null) return;
        synchronized (staticNodes) { staticNodes.remove(node); }
        requestStaticRedraw();
    }

    void updateStaticInteraction(
            LauncherGlassStaticNode node, PrismalInteractionState interaction) {
        if (node == null || shuttingDown) return;
        synchronized (staticNodes) {
            StaticNodeState state = staticNodes.get(node);
            if (state == null) return;
            state.interaction = interaction != null ? interaction : PrismalInteractionState.IDLE;
        }
        requestStaticRedraw();
    }

    void requestLifecycleRefresh() {
        requestSceneRedraw();
    }

    void invalidateGeneration(long generation) {
        if (shuttingDown || generation < sceneGeneration) return;
        sceneGeneration = generation;
    }

    void requestFreshBackdrop(long generation) {
        if (shuttingDown || generation < sceneGeneration) return;
        if (rotationSettlePending && generation == sceneGeneration) {
            MainHook.log(TAG + " fresh backdrop deferred for rotation settle generation="
                    + generation + " rotation=" + rotationSettleTargetRotation);
            return;
        }
        clearWallpaperRequest();
        invalidateGeneration(generation);
        mainHandler.post(() -> recoverFreshBackdropOnUi(generation, 0));
    }

    boolean requestWallpaperBackdrop(
            long requestedSceneGeneration, long wallpaperGeneration, boolean authoritative) {
        if (shuttingDown || requestedSceneGeneration != sceneGeneration
                || wallpaperGeneration < 0L || rotationSettlePending) return false;
        synchronized (this) {
            wallpaperRequestedGeneration = wallpaperGeneration;
            wallpaperRequestedSceneGeneration = requestedSceneGeneration;
            wallpaperRequestedAuthoritative = authoritative;
        }
        sourceBackend.requestFresh(
                requestedSceneGeneration,
                WorkstationProducerPolicy.shouldUseSingleFramePulse(MainHook.isWorkstationMode()));
        return true;
    }

    void cancelWallpaperBackdrop(long wallpaperGeneration) {
        synchronized (this) {
            if (wallpaperRequestedGeneration != wallpaperGeneration) return;
            clearWallpaperRequestLocked();
        }
    }

    private void clearWallpaperRequest() {
        synchronized (this) { clearWallpaperRequestLocked(); }
    }

    private void clearWallpaperRequestLocked() {
        wallpaperRequestedGeneration = -1L;
        wallpaperRequestedSceneGeneration = -1L;
        wallpaperRequestedAuthoritative = false;
    }

    private WallpaperFrameToken takeWallpaperFrameToken(long frameSceneGeneration) {
        synchronized (this) {
            if (wallpaperRequestedGeneration < 0L
                    || wallpaperRequestedSceneGeneration != frameSceneGeneration) {
                return WallpaperFrameToken.NONE;
            }
            WallpaperFrameToken token = new WallpaperFrameToken(
                    wallpaperRequestedGeneration, wallpaperRequestedAuthoritative);
            clearWallpaperRequestLocked();
            return token;
        }
    }

    void requestSceneRedraw() {
        scheduleOutputRender(true, true);
    }

    void requestDragRedraw() {
        scheduleOutputRender(false, true);
    }

    void requestStaticRedraw() {
        scheduleOutputRender(true, false);
    }

    void onWorkspaceScrollMutation(int beforeScrollX, int afterScrollX) {
        if (shuttingDown || beforeScrollX == afterScrollX) return;
        workspaceScrollProjection.onScrollMutation(beforeScrollX, afterScrollX);
        requestStaticRedraw();
    }

    void resetWorkspaceScrollProjection() {
        workspaceScrollProjection.reset();
    }

    void suspendWorkspaceProducer() {
        if (shuttingDown) return;
        if (WorkstationProducerPolicy.shouldPauseSharedProducer(
                true, MainHook.isWorkstationMode())) {
            sourceBackend.setUpdatesEnabled(false, "launcher-coverage");
        }
    }

    void attachOutput(LauncherGlassSinkView sink, Surface surface, int width, int height) {
        if (sink == null || surface == null) return;
        if (!postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureLauncherGl();
                OutputState previous = outputs.remove(sink);
                releaseOutput(previous);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                outputs.put(sink, next);
                scheduleOutputRender(false, true);
            } catch (Throwable error) {
                MainHook.log(TAG + " attach output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        })) surface.release();
    }

    void resizeOutput(LauncherGlassSinkView sink, int width, int height) {
        if (sink == null || shuttingDown) return;
        postRender(() -> {
            OutputState output = outputs.get(sink);
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                scheduleOutputRender(false, true);
            }
        });
    }

    void detachOutput(LauncherGlassSinkView sink, Surface surface) {
        if (sink == null || shuttingDown) {
            if (surface != null) surface.release();
            return;
        }
        if (!postRender(() -> {
            OutputState output = outputs.remove(sink);
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }) && surface != null) surface.release();
    }

    void attachStaticOutput(Surface surface, int width, int height) {
        if (surface == null) return;
        if (!postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureLauncherGl();
                releaseOutput(staticOutput);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                staticOutput = next;
                mainHandler.post(() -> {
                    if (shuttingDown) return;
                    View root = rootRef.get();
                    if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
                });
            } catch (Throwable error) {
                MainHook.log(TAG + " attach static output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        })) surface.release();
    }

    void resizeStaticOutput(int width, int height) {
        if (shuttingDown) return;
        postRender(() -> {
            OutputState output = staticOutput;
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                scheduleOutputRender(true, false);
            }
        });
    }

    void detachStaticOutput(Surface surface) {
        if (shuttingDown) {
            if (surface != null) surface.release();
            return;
        }
        if (!postRender(() -> {
            OutputState output = staticOutput;
            staticOutput = null;
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }) && surface != null) surface.release();
    }

    private boolean postRender(Runnable action) {
        return !shuttingDown && sourceBackend.postToRenderThread(action);
    }

    private void scheduleOutputRender(boolean staticDirty, boolean dragDirty) {
        if (shuttingDown) return;
        boolean shouldPost = false;
        synchronized (outputWorkLock) {
            pendingStaticRender |= staticDirty;
            pendingDragRender |= dragDirty;
            if (!outputRenderQueued) {
                outputRenderQueued = true;
                shouldPost = true;
            }
        }
        if (shouldPost && !postRender(this::drainOutputRenderWork)) {
            synchronized (outputWorkLock) { outputRenderQueued = false; }
        }
    }

    private void drainOutputRenderWork() {
        boolean renderStatic;
        boolean renderDrag;
        synchronized (outputWorkLock) {
            renderStatic = pendingStaticRender;
            renderDrag = pendingDragRender;
            pendingStaticRender = false;
            pendingDragRender = false;
        }
        try {
            if (backdropPrepared) renderOutputs(renderStatic, renderDrag);
        } catch (Throwable error) {
            MainHook.log(TAG + " output redraw failed " + debugLabel() + ": " + error);
        }

        boolean repost = false;
        synchronized (outputWorkLock) {
            if (pendingStaticRender || pendingDragRender) repost = true;
            else outputRenderQueued = false;
        }
        if (repost && !postRender(this::drainOutputRenderWork)) {
            synchronized (outputWorkLock) { outputRenderQueued = false; }
        }
    }

    private void installRootObserver() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (rootObserver == observer && preDrawListener != null && observer.isAlive()) return;
        removeRootObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            syncSceneOnUiThread();
            return true;
        };
        observer.addOnPreDrawListener(listener);
        rootObserver = observer;
        preDrawListener = listener;
    }

    private void removeRootObserver() {
        ViewTreeObserver observer = rootObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        rootObserver = null;
        preDrawListener = null;
        if (observer != null && listener != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(listener); }
            catch (Throwable ignored) {}
        }
    }

    private void syncSceneOnUiThread() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();
        int nextHeight = root.getHeight();
        boolean rootGeometryChanged = nextWidth > 0 && nextHeight > 0
                && (nextWidth != rootWidth || nextHeight != rootHeight);
        boolean dragChanged = rootGeometryChanged;
        boolean staticChanged = rootGeometryChanged;
        if (nextWidth > 0) rootWidth = nextWidth;
        if (nextHeight > 0) rootHeight = nextHeight;

        List<NodeState> dragSnapshot;
        synchronized (nodes) { dragSnapshot = new ArrayList<>(nodes.values()); }
        for (NodeState node : dragSnapshot) {
            LauncherGlassSinkView sink = node.sinkRef.get();
            if (sink == null) continue;
            boolean localChanged = sink.syncFromMaterial();
            dragChanged |= localChanged;
            if (!rootGeometryChanged && !localChanged) continue;
            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = node.geometry;
            if ((old == null) != (observed == null)
                    || (old != null && !old.sameAs(observed))) {
                node.geometry = observed;
                dragChanged = true;
            }
        }

        Integer workspaceScrollX = LauncherGlassStaticLayer.captureWorkspaceScrollAnchor(root);
        List<StaticNodeState> staticSnapshot;
        synchronized (staticNodes) { staticSnapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : staticSnapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            if (node == null) continue;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);
            StaticGeometryFrame oldFrame = state.frame;
            LauncherGlassGeometry.Snapshot old = oldFrame != null ? oldFrame.geometry : null;
            if (observed == null && old != null && node.retainLastGeometryDuringFade()) continue;
            if ((old == null) != (observed == null)
                    || (old != null && !old.sameAs(observed))) {
                int anchor = workspaceScrollX != null
                        ? workspaceScrollX : oldFrame != null ? oldFrame.workspaceScrollX : 0;
                boolean anchorValid = workspaceScrollX != null
                        || (oldFrame != null && oldFrame.workspaceScrollValid);
                state.frame = new StaticGeometryFrame(observed, anchor, anchorValid);
                staticChanged = true;
            }
        }

        int nextRotation = readLauncherConfigRotation(root);
        if (nextRotation != configRotation) {
            configRotation = nextRotation;
            beginRotationSettle(nextRotation);
            sourceBackend.setUpdatesEnabled(false, "launcher-rotation-settle");
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            scheduleRotationSettle(root, nextRotation);
            return;
        }

        boolean sourceGeometryChanged = sourceBackend.reconcileRoot();
        if (sourceGeometryChanged) {
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            return;
        }
        if (rootGeometryChanged) {
            sourceBackend.requestFresh(
                    sceneGeneration,
                    WorkstationProducerPolicy.shouldUseSingleFramePulse(MainHook.isWorkstationMode()));
            return;
        }
        if (staticChanged || dragChanged) scheduleOutputRender(staticChanged, dragChanged);
    }

    private void recoverFreshBackdropOnUi(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }
        installRootObserver();
        if (readLauncherConfigRotation(root) != configRotation || rotationSettlePending) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }
        boolean sourceChanged = sourceBackend.reconcileRoot();
        if (sourceChanged) {
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            return;
        }
        if (generation != sceneGeneration) return;
        sourceBackend.requestFresh(
                generation,
                WorkstationProducerPolicy.shouldUseSingleFramePulse(MainHook.isWorkstationMode()));
    }

    private void retryFreshBackdropRecovery(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration
                || attempt >= MAX_BIND_RETRY_FRAMES) return;
        View root = rootRef.get();
        if (root != null) {
            root.postOnAnimation(() -> recoverFreshBackdropOnUi(generation, attempt + 1));
        }
    }

    private void beginRotationSettle(int targetRotation) {
        rotationSettleSerial++;
        rotationSettlePending = true;
        rotationSettleTargetRotation = targetRotation;
    }

    private void scheduleRotationSettle(View root, int targetRotation) {
        if (shuttingDown || root == null) return;
        final long serial = rotationSettleSerial;
        final float ratio = readLauncherTransitionDurationRatio(root);
        final long delayMs = LauncherGlassRotationSettlePolicy.settleDelayMs(ratio);
        MainHook.log(TAG + " rotation capture gated rotation=" + targetRotation
                + " delayMs=" + delayMs + " ratio=" + ratio + " serial=" + serial);
        mainHandler.postDelayed(() -> {
            if (shuttingDown || !rotationSettlePending || serial != rotationSettleSerial
                    || configRotation != targetRotation) return;
            View liveRoot = rootRef.get();
            if (liveRoot == null || !liveRoot.isAttachedToWindow()) return;
            liveRoot.postOnAnimation(() -> finishRotationSettle(serial, targetRotation));
        }, delayMs);
    }

    private void finishRotationSettle(long serial, int targetRotation) {
        if (shuttingDown || !rotationSettlePending || serial != rotationSettleSerial
                || configRotation != targetRotation) return;
        rotationSettlePending = false;
        rotationSettleTargetRotation = -1;
        sourceBackend.reconcileRoot();
        if (!sourceBackend.isRebindPending()) {
            sourceBackend.requestRebind("launcher-rotation-settle");
        }
        MainHook.log(TAG + " rotation capture released rotation=" + targetRotation
                + " serial=" + serial);
        View root = rootRef.get();
        if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
    }

    private float readLauncherTransitionDurationRatio(View root) {
        if (root == null) return 1f;
        try {
            ClassLoader loader = root.getContext().getClassLoader();
            Class<?> helper = Class.forName(
                    "com.miui.home.recents.TransitionAnimDurationHelper", false, loader);
            Method getInstance = helper.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object instance = getInstance.invoke(null);
            Method getRatio = helper.getDeclaredMethod("getAnimDurationRatio");
            getRatio.setAccessible(true);
            Object value = getRatio.invoke(instance);
            if (value instanceof Number) return ((Number) value).floatValue();
        } catch (Throwable error) {
            MainHook.log(TAG + " transition duration ratio unavailable: " + error);
        }
        return 1f;
    }

    private static int readLauncherConfigRotation(View view) {
        Display display = view != null ? view.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        try {
            Method method = Display.class.getMethod("getInstallOrientation");
            Object value = method.invoke(display);
            if (value instanceof Number) installOrientation = ((Number) value).intValue();
        } catch (Throwable ignored) {}
        int result = (installOrientation + display.getRotation()) % 4;
        return result < 0 ? result + 4 : result;
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null
                || frame.generation != sceneGeneration || rotationSettlePending) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        PrismalParams params = prismalParams;
        if (params == null) return;
        try {
            ensureLauncherGl();
            rootWidth = frame.logicalWidth;
            rootHeight = frame.logicalHeight;
            sourceBackend.makePbufferCurrent();
            prismalRenderer.prepareBackdrop(
                    frame.normalizedTextureId,
                    frame.physicalWidth,
                    frame.physicalHeight,
                    frame.logicalWidth,
                    frame.logicalHeight,
                    params);
            backdropPrepared = true;
            renderOutputs(true, true);
            WallpaperFrameToken wallpaperFrame = takeWallpaperFrameToken(frame.generation);
            boolean renderedStaticOutput = staticOutput != null;
            long renderedGeneration = frame.generation;
            if (WorkstationProducerPolicy.shouldPauseAfterFrameConsumed(MainHook.isWorkstationMode())) {
                sourceBackend.setUpdatesEnabled(false, "launcher-workstation-frame-consumed");
            }
            if (renderedStaticOutput) {
                // Queue one render-thread turn after this callback. RootPassBlurBackend publishes
                // freshness only after onFreshFrame returns, so the main-thread authority callback
                // cannot race ahead of the production freshness state.
                sourceBackend.postToRenderThread(() -> {
                    if (shuttingDown || !sourceBackend.hasFreshFrame(renderedGeneration)
                            || renderedGeneration != sceneGeneration) return;
                    mainHandler.post(() -> {
                        if (shuttingDown || rootRef.get() != root || !ownsRoot(root)
                                || renderedGeneration != sceneGeneration
                                || !sourceBackend.hasFreshFrame(renderedGeneration)) return;
                        LauncherGlassSceneController.onFreshFrameRendered(
                                root,
                                renderedGeneration,
                                wallpaperFrame.generation,
                                wallpaperFrame.authoritative);
                    });
                });
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " fresh Prismal render failed " + debugLabel() + ": " + error);
            throw error;
        }
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (shuttingDown || generation != sceneGeneration) return;
        MainHook.log(TAG + " source backend failed closed " + debugLabel()
                + " generation=" + generation + ": " + error);
    }

    private void ensureLauncherGl() {
        sourceBackend.makePbufferCurrent();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
    }

    private void renderOutputs(boolean renderStatic, boolean renderDrag) {
        PrismalParams params = prismalParams;
        if (!backdropPrepared || params == null || rootWidth <= 0 || rootHeight <= 0) return;
        if (renderStatic) renderStaticScene(params);
        if (renderDrag) renderDragOutputs(params);
    }

    private void renderStaticScene(PrismalParams params) {
        OutputState output = staticOutput;
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE) return;
        sourceBackend.makePbufferCurrent();
        prismalRenderer.beginGlassFrame();
        List<StaticNodeState> snapshot;
        synchronized (staticNodes) { snapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : snapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            StaticGeometryFrame frame = state.frame;
            LauncherGlassGeometry.Snapshot geometry = frame != null ? frame.geometry : null;
            if (node == null || geometry == null) continue;
            float projectedCenterX = workspaceScrollProjection.projectCenterX(
                    geometry.centerX, frame.workspaceScrollX, frame.workspaceScrollValid);
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, projectedCenterX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    node.nodeKind(), launcherHighlightProfile, largeSurfaceHighlightProfile);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, highlights, state.interaction, node.visibilityAlpha());
        }
        presentFull(prismalRenderer.outputTexture(), output);
    }

    private void renderDragOutputs(PrismalParams params) {
        for (Map.Entry<LauncherGlassSinkView, OutputState> entry
                : new ArrayList<>(outputs.entrySet())) {
            NodeState node = nodes.get(entry.getKey());
            LauncherGlassGeometry.Snapshot geometry = node != null ? node.geometry : null;
            if (node == null || geometry == null) continue;
            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            LauncherGlassSinkView sink = node.sinkRef.get();
            LauncherGlassNodeKind kind = sink != null
                    ? sink.nodeKind() : LauncherGlassNodeKind.LARGE_FOLDER;
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    kind, launcherHighlightProfile, largeSurfaceHighlightProfile);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, highlights, node.interaction);
            present(prismalRenderer.outputTexture(), geometry, entry.getValue());
        }
    }

    private void presentFull(int sceneTexture, OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        sourceBackend.makeCurrent(output.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, output.width, output.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        sourceBackend.swapBuffers(output.eglSurface);
    }

    private void present(
            int sceneTexture,
            LauncherGlassGeometry.Snapshot geometry,
            OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        sourceBackend.makeCurrent(output.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, output.width, output.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                geometry.cropLeft, geometry.cropBottom, geometry.cropWidth, geometry.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        sourceBackend.swapBuffers(output.eglSurface);
    }

    private void releaseOutput(OutputState output) {
        if (output == null) return;
        if (output.eglSurface != EGL14.EGL_NO_SURFACE) {
            try { sourceBackend.destroyWindowSurface(output.eglSurface); }
            catch (Throwable ignored) {}
            output.eglSurface = EGL14.EGL_NO_SURFACE;
        }
        try { output.surface.release(); } catch (Throwable ignored) {}
    }

    boolean suspendProducerForUnlockCapture() {
        if (shuttingDown || !sourceBackend.hasBinding()) return false;
        sourceBackend.setUpdatesEnabled(false, "launcher-unlock-capture");
        return true;
    }

    boolean rebindProducer() {
        return rebindProducer((LauncherGlassSessionRegistry.RolloverCompletion) null);
    }

    boolean rebindProducer(LauncherGlassSessionRegistry.RolloverCompletion rolloverComplete) {
        if (shuttingDown || sourceBackend.isShutdown()) {
            MainHook.log(TAG + " unlock endpoint rollover rejected " + diagnosticSessionId()
                    + " reason=shutdown");
            return false;
        }
        boolean accepted = sourceBackend.requestRebind(
                "launcher-endpoint-rollover", rolloverComplete);
        if (!accepted) {
            MainHook.log(TAG + " unlock endpoint rollover rejected " + diagnosticSessionId()
                    + " reason=backend-request");
        }
        return accepted;
    }

    boolean rebindWorkstationProducer(String reason, long generation) {
        if (shuttingDown || sourceBackend.isShutdown() || sourceBackend.isRebindPending()) {
            logWorkstationProducerRollover(reason, generation, "REJECTED", "request", null);
            return false;
        }
        sourceBackend.requestRebind(reason);
        if (!sourceBackend.isRebindPending()) {
            logWorkstationProducerRollover(reason, generation, "REJECTED", "request", null);
            return false;
        }
        sourceBackend.postToRenderThread(() -> {
            boolean success = !sourceBackend.isActivationExhausted() && !sourceBackend.isShutdown();
            logWorkstationProducerRollover(
                    reason,
                    generation,
                    success ? "ACCEPTED" : "FAILED",
                    success ? "endpoint-recreated" : "endpoint-recreate",
                    null);
        });
        return true;
    }

    private void logWorkstationProducerRollover(
            String reason, long generation, String result, String stage, Throwable error) {
        MainHook.log(TAG + "[ProducerRecovery] reason=" + reason
                + " session=" + diagnosticSessionId()
                + " generation=" + generation
                + " result=" + result + " stage=" + stage
                + (error != null ? " error=" + error : ""));
    }

    void shutdown() {
        if (shuttingDown) return;
        MainHook.log(TAG + " shutdown " + debugLabel());
        shuttingDown = true;
        rotationSettleSerial++;
        rotationSettlePending = false;
        rotationSettleTargetRotation = -1;
        clearWallpaperRequest();
        View root = rootRef.get();
        removeRootObserver();
        if (root != null) {
            try { root.removeOnAttachStateChangeListener(rootAttachListener); }
            catch (Throwable ignored) {}
            LauncherGlassSessionRegistry.forget(root, this);
        }
        boolean queued = sourceBackend.postToRenderThread(() -> {
            releaseLauncherGl();
            sourceBackend.shutdown();
        });
        if (!queued) sourceBackend.shutdown();
    }

    private void releaseLauncherGl() {
        releaseOutput(staticOutput);
        staticOutput = null;
        for (OutputState output : new ArrayList<>(outputs.values())) releaseOutput(output);
        outputs.clear();
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
        compositeProgram = 0;
        backdropPrepared = false;
    }

    private void bindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) throw new IllegalStateException("quad attribute unavailable");
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
    }

    private void unbindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("program link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("shader compile failed: " + log);
        }
        return shader;
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing uniform " + name);
        return location;
    }
}
