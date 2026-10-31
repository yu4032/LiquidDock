package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalIorLensPolicy;
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
    private static final long PERF_LOG_WINDOW_NS = 3_000_000_000L;
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
        final float outputAlpha;

        StaticGeometryFrame(
                LauncherGlassGeometry.Snapshot geometry,
                int workspaceScrollX,
                boolean workspaceScrollValid,
                float outputAlpha) {
            this.geometry = geometry;
            this.workspaceScrollX = workspaceScrollX;
            this.workspaceScrollValid = workspaceScrollValid;
            this.outputAlpha = outputAlpha;
        }
    }

    private static final class StaticNodeState {
        final WeakReference<LauncherGlassStaticNode> nodeRef;
        volatile StaticGeometryFrame frame;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        // Render-thread cache. Backdrop frames change far more often than node geometry.
        StaticGeometryFrame renderedFrame;
        PrismalGeometry renderedGeometry;
        int renderedRootWidth = -1;
        int renderedRootHeight = -1;
        float renderedProjectedCenterX = Float.NaN;

        StaticNodeState(LauncherGlassStaticNode node) {
            nodeRef = new WeakReference<>(node);
        }
    }

    /** Static geometry frames published together after the UI traversal. */
    private static final class StaticSceneSnapshot {
        static final StaticSceneSnapshot EMPTY =
                new StaticSceneSnapshot(new StaticNodeState[0]);
        final StaticNodeState[] nodes;
        final StaticGeometryFrame[] frames;

        StaticSceneSnapshot(StaticNodeState[] nodes) {
            this.nodes = nodes;
            frames = new StaticGeometryFrame[nodes.length];
            for (int i = 0; i < nodes.length; i++) frames[i] = nodes[i].frame;
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
    // One GL-thread source for Workspace and its two Recents capsule outputs.
    private volatile RecentsCapsuleGlassSession recentsConsumer;
    private final LauncherGlassScrollProjectionState workspaceScrollProjection =
            new LauncherGlassScrollProjectionState();
    // UI-only scratch, cleared at the start of every Workspace static pre-draw.
    private final HomeGridCellGeometryHook.WidgetWidthFrame widgetWidthFrame =
            new HomeGridCellGeometryHook.WidgetWidthFrame();
    private final LauncherGlassStaticGeometryCache.Frame staticViewFrame =
            new LauncherGlassStaticGeometryCache.Frame();
    // UI-thread scratch. Root-to-global is common to every Workspace node in one pre-draw, so
    // invert it once instead of repeating the same matrix walk/inversion for every icon/widget.
    private final Matrix uiRootToGlobal = new Matrix();
    private final Matrix uiGlobalToRoot = new Matrix();
    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LauncherGlassStaticNode, StaticNodeState> staticNodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private volatile NodeState[] nodeStateSnapshot = new NodeState[0];
    private volatile StaticNodeState[] staticNodeStateSnapshot = new StaticNodeState[0];
    private volatile StaticSceneSnapshot publishedStaticScene = StaticSceneSnapshot.EMPTY;
    // GL-thread trace counter for superseded output frames.
    private long staleStaticSwapDrops;
    // Render-thread only. EGL surfaces are created through sourceBackend's shared EGL context.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();
    private final LauncherGlassOutputRenderState outputRenderState =
            new LauncherGlassOutputRenderState();
    private final boolean workspaceSource;

    // Preserve last composed Workspace output while an expanded folder owns
    // its own frozen backdrop. No new GPU sample or scene redraw is permitted.
    private volatile boolean folderFrameFrozen;
    // A transition may settle before its requested source generation has arrived. Keep the
    // producer live until that frame is rendered, then return Workspace to its idle pause.
    private boolean pauseWorkspaceProducerWhenFresh;
    private int hwuiProbeBackdropLogicalWidth = -1;
    private int hwuiProbeBackdropLogicalHeight = -1;
    private int hwuiProbeBackdropRotation = -1;
    private long hwuiProbeBackdropWallpaperGeneration = -1L;
    private boolean hwuiProbeBackdropWallpaperAuthoritative;
    // GL produces immutable buffers; only the newest export may be installed on the UI thread.
    private volatile long hwuiProbeExportSerial;
    private volatile boolean hwuiProbeExportDirty = true;
    private boolean hwuiProbeSelectionLogged;
    // A live floating-icon owner is the precise HOME/app transition boundary. Drop optional rim
    // layers and reconstructed-27 optics only while that proxy moves; geometry/backdrop stay live.
    private volatile boolean workspaceMotionQualityActive;
    private final float[] hwuiProbeInvariants = new float[10];
    private volatile boolean shuttingDown;
    private volatile Runnable terminalFailureListener;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile launcherHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile PrismalHighlightProfile largeSurfaceHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile int rootWidth;
    private volatile int rootHeight;
    private volatile int configRotation;
    private volatile long sceneGeneration = 1L;
    private volatile float appliedGlassBlur = Float.NaN;
    private volatile int passBlurCaptureScalePercent =
            PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
    private volatile int passBlurRenderFps = PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
    private final WorkspaceTransitionFrameSyncState transitionFrameSync =
            new WorkspaceTransitionFrameSyncState();

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
    private int compositePositionLocation = -1;
    private int compositeUvLocation = -1;
    private int compositeTextureLocation = -1;
    private int compositeCropRectLocation = -1;
    private volatile boolean backdropPrepared;
    // One bounded recovery request per transient output/producer geometry mismatch.
    private long lastGeometryMismatchRefreshMs;

    // Debug-only aggregate timings. UI and render counters are single-thread owned.
    private long uiPerfWindowStartNs;
    private long uiPerfTotalNs;
    private long uiPerfMaxNs;
    private int uiPerfFrames;
    private int hwuiProbeDrawsWindow;
    private long glPerfWindowStartNs;
    private long glPerfBackdropNs;
    private long glPerfSceneNs;
    private long glPerfTotalNs;
    private long glPerfMaxNs;
    private int glPerfFrames;

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
        this(root, glassConfig, PassBlurBindRequest.launcherWorkspace(root, 1.0f));
    }

    LauncherGlassSession(
            View root, LiquidDockConfig.Glass glassConfig, PassBlurBindRequest bindRequest) {
        if (bindRequest == null) throw new IllegalArgumentException("bindRequest == null");
        workspaceSource = bindRequest.domain() == PassBlurDomain.LAUNCHER_WORKSPACE;
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
                bindRequest,
                passBlurCaptureScalePercent,
                passBlurRenderFps,
                this,
                "LiquidDock-LauncherGlass-EGL");
        root.addOnAttachStateChangeListener(rootAttachListener);
        installRootObserver();
        MainHook.log(TAG + " " + debugLabel()
                + " created source=RootPassBlurBackend domain=" + bindRequest.domain());
    }

    boolean isShutdown() {
        return shuttingDown;
    }

    /** A Recents child shares this session's native producer, EGL context and render thread. */
    synchronized RootPassBlurBackend attachRecentsConsumer(RecentsCapsuleGlassSession child) {
        if (shuttingDown || !workspaceSource || child == null
                || (recentsConsumer != null && recentsConsumer != child)) return null;
        recentsConsumer = child;
        MainHook.log("[DC][RecentsCapsule] shared Workspace root PassBlur source attached");
        return sourceBackend;
    }

    synchronized void detachRecentsConsumer(RecentsCapsuleGlassSession child) {
        if (recentsConsumer != child) return;
        recentsConsumer = null;
        reconcileSourceAfterRecentsConsumerGone("launcher-recents-detached");
    }

    void onRecentsConsumerHidden(RecentsCapsuleGlassSession child) {
        if (shuttingDown || recentsConsumer != child || child.isRecentsVisible()) return;
        reconcileSourceAfterRecentsConsumerGone("launcher-recents-hidden");
    }

    private void reconcileSourceAfterRecentsConsumerGone(String reason) {
        if (shuttingDown || hasVisibleRecentsConsumer()) return;
        // A visible Recents child keeps the source live. Once the final child exits, normal HOME
        // returns to idle capture policy; Workstation deliberately keeps its source live.
        if (WorkstationProducerPolicy.shouldPauseAfterRecentsConsumerGone(
                folderFrameFrozen, MainHook.isWorkstationMode())) {
            pauseWorkspaceProducerWhenFresh = false;
            sourceBackend.setUpdatesEnabled(false, reason);
        }
    }

    boolean hasVisibleRecentsConsumer() {
        RecentsCapsuleGlassSession child = recentsConsumer;
        return !shuttingDown && child != null && child.isRecentsVisible();
    }

    void resumeRecentsSharedSource(RecentsCapsuleGlassSession child) {
        if (shuttingDown || recentsConsumer != child || sourceBackend.isShutdown()) return;
        // The SceneController pauses this *same* producer when Recents covers Workspace.
        // Resume it after coverage, without creating or re-publishing a Surface binder.
        sourceBackend.requestFresh(sceneGeneration, false);
        sourceBackend.setUpdatesEnabled(true, "recents-shared-root-live");
        MainHook.log("[DC][RecentsCapsule] shared source resumed generation="
                + sceneGeneration);
    }

    boolean ensureLiveDragSource() {
        if (shuttingDown) return false;
        sourceBackend.setUpdatesEnabled(true, "launcher-drag-live");
        if (!backdropPrepared) requestFreshBackdrop(sceneGeneration);
        return true;
    }

    boolean hasPreparedBackdrop() {
        return !shuttingDown && backdropPrepared;
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

    void setTerminalFailureListener(Runnable listener) {
        terminalFailureListener = listener;
    }

    void setPrismalParams(PrismalParams params) {
        if (shuttingDown || params == null) return;
        prismalParams = params;
        // Material-only changes (for example MIUIX dialog dim alpha) do not invalidate the
        // captured backdrop. Re-render the prepared scene instead of recapturing the producer.
        if (backdropPrepared) requestSceneRedraw();
    }

    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {
        if (shuttingDown) return;
        hwuiProbeExportDirty = true;
        applyGlassConfig(glassConfig);
        sourceBackend.setQuality(passBlurCaptureScalePercent, passBlurRenderFps);
        mainHandler.post(() -> {
            if (shuttingDown) return;
            syncSceneOnUiThread();
            View root = rootRef.get();
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
        });
    }

    /** Optical-only updates reuse the prepared PassBlur backdrop and existing EGL outputs. */
    void applyLiveGlassConfig(LiquidDockConfig.Glass glassConfig) {
        if (shuttingDown || glassConfig == null) return;
        boolean qualityChanged = passBlurCaptureScalePercent != glassConfig.passBlurCaptureScalePercent
                || passBlurRenderFps != glassConfig.passBlurRenderFps;
        boolean blurChanged = Float.compare(appliedGlassBlur, glassConfig.blur) != 0;
        applyGlassConfig(glassConfig);
        if (qualityChanged) sourceBackend.setQuality(passBlurCaptureScalePercent, passBlurRenderFps);
        if (qualityChanged || blurChanged) {
            hwuiProbeExportDirty = true;
            // Gaussian blur lives in the prepared backdrop, not only in final uniforms.
            // Request a new producer frame without replacing the EGL / PassBlur owner.
            View root = rootRef.get();
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
        } else {
            requestSceneRedraw();
        }
    }

    private void applyGlassConfig(LiquidDockConfig.Glass glassConfig) {
        View root = rootRef.get();
        float density = root != null ? root.getResources().getDisplayMetrics().density : 1f;
        appliedGlassBlur = glassConfig != null ? glassConfig.blur : Float.NaN;
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
            if (!nodes.containsKey(sink)) {
                nodes.put(sink, new NodeState(sink));
                nodeStateSnapshot = nodes.values().toArray(new NodeState[0]);
            }
        }
        syncSceneOnUiThread();
        requestDragRedraw();
    }

    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) {
            nodes.remove(sink);
            nodeStateSnapshot = nodes.values().toArray(new NodeState[0]);
        }
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
            if (!staticNodes.containsKey(node)) {
                staticNodes.put(node, new StaticNodeState(node));
                staticNodeStateSnapshot =
                        staticNodes.values().toArray(new StaticNodeState[0]);
            }
        }
        publishStaticSceneSnapshot();
        syncSceneOnUiThread();
        requestStaticRedraw();
    }

    void unregisterStaticNode(LauncherGlassStaticNode node) {
        if (node == null) return;
        synchronized (staticNodes) {
            staticNodes.remove(node);
            staticNodeStateSnapshot =
                    staticNodes.values().toArray(new StaticNodeState[0]);
        }
        publishStaticSceneSnapshot();
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

    void requestFreshBackdrop() {
        requestFreshBackdrop(sceneGeneration);
    }

    void requestFreshBackdrop(long generation) {
        // The posted HOME recovery task must never wake a folder-paused root.
        // Recents owns its own explicit resumeRecentsSharedSource() request.
        if (shuttingDown || folderFrameFrozen || generation < sceneGeneration) return;
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
        if (shuttingDown || folderFrameFrozen || requestedSceneGeneration != sceneGeneration
                || wallpaperGeneration < 0L || rotationSettlePending) return false;
        synchronized (this) {
            wallpaperRequestedGeneration = wallpaperGeneration;
            wallpaperRequestedSceneGeneration = requestedSceneGeneration;
            wallpaperRequestedAuthoritative = authoritative;
        }
        sourceBackend.requestFresh(
                requestedSceneGeneration,
                workspaceSource
                        && WorkstationProducerPolicy.shouldUseSingleFramePulse(
                                MainHook.isWorkstationMode()));
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

    private WallpaperFrameToken peekWallpaperFrameToken(long frameSceneGeneration) {
        synchronized (this) {
            if (wallpaperRequestedGeneration < 0L
                    || wallpaperRequestedSceneGeneration != frameSceneGeneration) {
                return WallpaperFrameToken.NONE;
            }
            return new WallpaperFrameToken(
                    wallpaperRequestedGeneration, wallpaperRequestedAuthoritative);
        }
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

    void publishDragGeometry(
            LauncherGlassSinkView sink, LauncherGlassGeometry.Snapshot geometry) {
        if (sink == null || geometry == null || shuttingDown) return;
        synchronized (nodes) {
            NodeState node = nodes.get(sink);
            if (node == null) return;
            LauncherGlassGeometry.Snapshot old = node.geometry;
            if (old != null && old.sameAs(geometry)) return;
            node.geometry = geometry;
        }
        requestDragRedraw();
    }

    void requestStaticRedraw() {
        View root = rootRef.get();
        if (LauncherGlassStaticLayer.invalidateHwuiProbe(root)) return;
        scheduleOutputRender(true, false);
    }

    void onWorkspaceScrollMutation(int beforeScrollX, int afterScrollX) {
        if (shuttingDown || beforeScrollX == afterScrollX) return;
        workspaceScrollProjection.onScrollMutation(beforeScrollX, afterScrollX);
        View root = rootRef.get();
        if (!folderFrameFrozen
                && !LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()) {
            sourceBackend.setUpdatesEnabled(true, "launcher-workspace-scroll-motion");
        }
        applyTransitionFrameSync(
                transitionFrameSync.onWorkspaceScrollMutation(), root,
                "workspace-scroll-motion");
        if (LauncherGlassStaticLayer.invalidateHwuiProbe(root)) return;
        // Scroll geometry can redraw from the cached backdrop without a main-thread hop.
        scheduleOutputRender(true, false, true);
    }

    void resetWorkspaceScrollProjection() {
        workspaceScrollProjection.reset();
    }

    void setFolderFrameFrozen(boolean frozen) {
        if (folderFrameFrozen == frozen || shuttingDown) return;
        folderFrameFrozen = frozen;
        if (frozen && !hasVisibleRecentsConsumer()) {
            sourceBackend.setUpdatesEnabled(false, "launcher-folder-frame-frozen");
        }
        // HOME output remains frozen even when an explicit Recents consumer
        // temporarily resumes the same source. After the folder closes the
        // SceneController requests a new HOME generation.
        MainHook.log(TAG + " Workspace source/render freeze=" + frozen);
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
        scheduleOutputRender(staticDirty, dragDirty, false);
    }

    private void scheduleOutputRender(
            boolean staticDirty, boolean dragDirty, boolean immediate) {
        if (shuttingDown || folderFrameFrozen) return;
        if (outputRenderState.request(staticDirty, dragDirty)) {
            // Scroll mutations and completed UI pre-draw traversals use the direct GL queue.
            boolean posted = immediate
                    ? postRender(this::drainOutputRenderWork)
                    : postOutputRenderAfterUi();
            if (!posted) outputRenderState.onPostRejected();
        }
    }

    private void publishStaticSceneSnapshot() {
        publishedStaticScene = new StaticSceneSnapshot(staticNodeStateSnapshot);
    }

    private boolean postOutputRenderAfterUi() {
        // Collect every icon/proxy mutation in the current UI turn before rendering the batch.
        // Geometry remains uncapped so paging follows the native display cadence.
        return mainHandler.post(() -> {
            if (!postRender(this::drainOutputRenderWork)) {
                outputRenderState.onPostRejected();
            }
        });
    }

    private void drainOutputRenderWork() {
        int work = outputRenderState.consumeQueuedRender();
        boolean trace = Trace.isEnabled() && workspaceSource && work != 0;
        if (trace) Trace.beginSection("LD.Workspace.GeometryFrame");
        try {
            if (!folderFrameFrozen && backdropPrepared && work != 0) {
                renderOutputs(
                        (work & LauncherGlassOutputRenderState.STATIC) != 0,
                        (work & LauncherGlassOutputRenderState.DRAG) != 0);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " output redraw failed " + debugLabel() + ": " + error);
        } finally {
            if (trace) Trace.endSection();
        }

        if (outputRenderState.finishQueuedRender()
                && !postRender(this::drainOutputRenderWork)) {
            outputRenderState.onPostRejected();
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
        // Perfetto's app atrace slices work without enabling debugLogging.
        // Debug logging appends to a file synchronously and distorts UI costs.
        boolean tracing = Trace.isEnabled();
        if (tracing) Trace.beginSection("LD.Workspace.SceneSync");
        try {
            if (!MainHook.debugLogging) {
                syncSceneOnUiThreadInternal(tracing);
                return;
            }
            long startedNs = System.nanoTime();
            try {
                syncSceneOnUiThreadInternal(tracing);
            } finally {
                recordUiPerf(System.nanoTime() - startedNs);
            }
        } finally {
            if (tracing) Trace.endSection();
        }
    }

    private void syncSceneOnUiThreadInternal(boolean tracing) {
        if (shuttingDown || folderFrameFrozen) return;
        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();
        int nextHeight = root.getHeight();
        boolean rootGeometryChanged = nextWidth > 0 && nextHeight > 0
                && (nextWidth != rootWidth || nextHeight != rootHeight);
        boolean dragChanged = rootGeometryChanged;
        boolean staticChanged = rootGeometryChanged;
        boolean geometryMotionChanged = rootGeometryChanged;
        if (nextWidth > 0) rootWidth = nextWidth;
        if (nextHeight > 0) rootHeight = nextHeight;

        NodeState[] dragSnapshot = nodeStateSnapshot;
        int dragGeometryReads = 0;
        if (tracing) Trace.beginSection("LD.Workspace.DragNodes");
        try {
            for (NodeState node : dragSnapshot) {
                LauncherGlassSinkView sink = node.sinkRef.get();
                if (sink == null) continue;
                boolean localChanged = sink.syncFromMaterial();
                dragChanged |= localChanged;
                if (!rootGeometryChanged && !localChanged && node.geometry != null) continue;
                if (tracing) dragGeometryReads++;
                LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);
                LauncherGlassGeometry.Snapshot old = node.geometry;
                if ((old == null) != (observed == null)
                        || (old != null && !old.sameAs(observed))) {
                    node.geometry = observed;
                    dragChanged = true;
                    geometryMotionChanged = true;
                }
            }
        } finally {
            if (tracing) {
                Trace.endSection();
                Trace.setCounter("LD.Workspace.DragCandidates", dragSnapshot.length);
                Trace.setCounter("LD.Workspace.DragGeometryReads", dragGeometryReads);
            }
        }

        StaticNodeState[] staticSnapshot = staticNodeStateSnapshot;
        View workspaceMaterial = null;
        boolean launchProxyActive = false;
        boolean launchProxyReturning = false;
        float launchProxyReturnProgress = 0f;
        int staticGeometryReads = 0;
        int staticCacheHits = 0;
        int staticNullCacheHits = 0;
        int staticWidgetCacheHits = 0;
        widgetWidthFrame.reset();
        staticViewFrame.reset();
        // Native WindowElement/Recents/unlock may animate independently of View properties.
        // Keep their existing per-frame capture authority; only reuse genuinely stable nodes.
        boolean forceStaticCapture = staticSnapshot.length > 0
                && (rootGeometryChanged || rotationSettlePending
                || transitionFrameSync.isActive()
                || LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()
                || LauncherGlassSceneController.isRecentsCoveredByVendor()
                || LauncherGlassSceneController.isRecentsWallpaperSettlePendingForRoot(root));
        if (tracing) Trace.beginSection("LD.Workspace.StaticNodes");
        try {
            Integer workspaceScrollX = null;
            boolean rootTransformPrepared = false;
            boolean staticRootTransformValid = false;
            for (StaticNodeState state : staticSnapshot) {
                LauncherGlassStaticNode node = state.nodeRef.get();
                if (node == null) continue;
                if (workspaceMaterial == null) workspaceMaterial = node.materialHost();
                if (node.isLaunchProxyActive()) {
                    launchProxyActive = true;
                    if (node.isLaunchProxyReturning()) {
                        launchProxyReturning = true;
                        launchProxyReturnProgress = Math.max(
                                launchProxyReturnProgress, node.launchProxyReturnProgress());
                    }
                }
                if (forceStaticCapture) {
                    node.invalidateStaticGeometry();
                } else if (node.canReuseStaticGeometry(root, widgetWidthFrame, staticViewFrame)
                        && state.frame != null) {
                    if (tracing) {
                        staticCacheHits++;
                        if (state.frame.geometry == null) staticNullCacheHits++;
                        if (node.nodeKind() == LauncherGlassNodeKind.WIDGET) {
                            staticWidgetCacheHits++;
                        }
                    }
                    continue;
                }
                // On an unchanged Workspace this work is avoided entirely. When any
                // node changes, share the anchor/matrix inversion across captures.
                if (!rootTransformPrepared) {
                    workspaceScrollX = LauncherGlassStaticLayer.captureWorkspaceScrollAnchor(root);
                    staticRootTransformValid = prepareStaticRootCaptureTransform(root);
                    rootTransformPrepared = true;
                }
                if (tracing && staticRootTransformValid) staticGeometryReads++;
                LauncherGlassGeometry.Snapshot observed = staticRootTransformValid
                        ? node.captureGeometry(root, uiGlobalToRoot, rootWidth, rootHeight,
                                widgetWidthFrame)
                        : null;
                StaticGeometryFrame oldFrame = state.frame;
                LauncherGlassGeometry.Snapshot old = oldFrame != null ? oldFrame.geometry : null;
                if (observed == null && old != null && node.retainLastGeometryDuringFade()) continue;
                // A successfully captured empty scene is different from "never captured".
                // Without a frame token an invisible folder can never enter the validated
                // reuse path, even if its entire View ancestry remains unchanged.
                // Widgets/native proxies retain their original per-frame authority.
                if (oldFrame == null && observed == null && staticRootTransformValid
                        && node.nodeKind() != LauncherGlassNodeKind.WIDGET
                        && !node.isLaunchProxyActive()) {
                    state.frame = new StaticGeometryFrame(null,
                            workspaceScrollX != null ? workspaceScrollX : 0,
                            workspaceScrollX != null, 0f);
                }
                if ((old == null) != (observed == null)
                        || (old != null && !old.sameAs(observed))) {
                    int anchor = workspaceScrollX != null
                            ? workspaceScrollX : oldFrame != null ? oldFrame.workspaceScrollX : 0;
                    boolean anchorValid = workspaceScrollX != null
                            || (oldFrame != null && oldFrame.workspaceScrollValid);
                    float oldAlpha = oldFrame != null ? oldFrame.outputAlpha : 0f;
                    state.frame = new StaticGeometryFrame(observed, anchor, anchorValid, oldAlpha);
                    staticChanged = true;
                    geometryMotionChanged = true;
                }
            }
        } finally {
            if (tracing) {
                Trace.endSection();
                Trace.setCounter("LD.Workspace.StaticCandidates", staticSnapshot.length);
                Trace.setCounter("LD.Workspace.StaticGeometryReads", staticGeometryReads);
                Trace.setCounter("LD.Workspace.StaticCacheHits", staticCacheHits);
                Trace.setCounter("LD.Workspace.StaticNullCacheHits", staticNullCacheHits);
                if (staticSnapshot.length > 0) {
                    Trace.setCounter("LD.Workspace.StaticWidgetCacheHits", staticWidgetCacheHits);
                    Trace.setCounter("LD.Workspace.WidgetGridReads", widgetWidthFrame.gridReads);
                    Trace.setCounter("LD.Workspace.WidgetGridHits", widgetWidthFrame.gridHits);
                }
            }
        }

        float workspaceAlpha = LauncherGlassStaticLayer.syncWorkspacePresentation(
                root, workspaceMaterial, launchProxyActive,
                launchProxyReturning, launchProxyReturnProgress);
        View workspaceRoot = LauncherGlassHierarchy.findWorkspaceRoot(workspaceMaterial);
        for (StaticNodeState state : staticSnapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            if (node == null) continue;
            float outputAlpha = node.presentationAlpha(
                    root, workspaceRoot, workspaceAlpha, launchProxyActive);
            StaticGeometryFrame oldFrame = state.frame;
            if (oldFrame != null && Float.compare(oldFrame.outputAlpha, outputAlpha) == 0) {
                continue;
            }
            state.frame = oldFrame == null
                    ? new StaticGeometryFrame(null, 0, false, outputAlpha)
                    : new StaticGeometryFrame(oldFrame.geometry, oldFrame.workspaceScrollX,
                            oldFrame.workspaceScrollValid, outputAlpha);
            staticChanged = true;
        }

        boolean nextMotionQualityActive = launchProxyActive;
        if (workspaceMotionQualityActive != nextMotionQualityActive) {
            workspaceMotionQualityActive = nextMotionQualityActive;
            staticChanged = true;
        }

        // Source rebind and rotation may return before output scheduling. Publish the
        // captured scene first so recovery renders use the same UI geometry revision.
        if (staticChanged) publishStaticSceneSnapshot();

        if (geometryMotionChanged
                && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()
                && !transitionFrameSync.isUnlockTransitionActive()) {
            // Open the producer gate before any resume or endpoint rebind. The separate scene
            // freshness barrier remains pending until SystemUI GONE.
            LauncherGlassHomePresentationHook.onWorkspaceUnlockMotionStarted();
            setUnlockTransitionFrameSyncEnabled(true, "unlock-motion-hold");
            sourceBackend.setUpdatesEnabled(true, "launcher-unlock-motion-live");
            MainHook.log("[DC][WorkspaceFrameSync] unlock producer gate opened; resume/rebind allowed; hold-until-GONE");
        }
        applyTransitionFrameSync(
                transitionFrameSync.onPreDraw(geometryMotionChanged), root);

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

        boolean sourceGeometryChanged;
        if (tracing) Trace.beginSection("LD.Workspace.SourceReconcile");
        try {
            sourceGeometryChanged = sourceBackend.reconcileRoot();
        } finally {
            if (tracing) Trace.endSection();
        }
        if (sourceGeometryChanged) {
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) {
                sceneGeneration = LauncherGlassHomePresentationHook.generationAfterEndpointChange(
                        sceneGeneration, nextGeneration, rotationSettlePending);
            }
            return;
        }
        if (rootGeometryChanged) {
            sourceBackend.requestFresh(
                    sceneGeneration,
                    workspaceSource
                            && WorkstationProducerPolicy.shouldUseSingleFramePulse(
                                    MainHook.isWorkstationMode()));
            return;
        }
        if (staticChanged || dragChanged) {
            // Scroll-only redraws are already queued by the mutation hook.
            boolean probeInvalidated = staticChanged
                    && LauncherGlassStaticLayer.invalidateHwuiProbe(root);
            boolean renderStatic = staticChanged && !probeInvalidated;
            if (renderStatic || dragChanged) {
                scheduleOutputRender(renderStatic, dragChanged, true);
            }
        }
    }

    private boolean prepareStaticRootCaptureTransform(View root) {
        if (root == null) return false;
        uiRootToGlobal.reset();
        root.transformMatrixToGlobal(uiRootToGlobal);
        uiGlobalToRoot.reset();
        return uiRootToGlobal.invert(uiGlobalToRoot);
    }

    void setNativeTransitionFrameSyncEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        View root = rootRef.get();
        applyTransitionFrameSync(
                transitionFrameSync.onNativeTransition(enabled),
                root,
                reason != null ? reason : "workspace-native-transition");
    }

    void setUnlockTransitionFrameSyncEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        View root = rootRef.get();
        applyTransitionFrameSync(
                transitionFrameSync.onUnlockTransition(enabled),
                root,
                reason != null ? reason : "workspace-unlock-transition");
    }

    private void applyTransitionFrameSync(
            WorkspaceTransitionFrameSyncState.Decision decision, View root) {
        applyTransitionFrameSync(decision, root, "workspace-geometry-motion");
    }

    private void applyTransitionFrameSync(
            WorkspaceTransitionFrameSyncState.Decision decision, View root, String reason) {
        if (decision == null) return;
        if (decision.enable) {
            if (!folderFrameFrozen
                    && !LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()) {
                sourceBackend.setUpdatesEnabled(true, reason + "-live-capture");
            }
            sourceBackend.setTransitionFrameSyncEnabled(true, reason);
        } else if (decision.disable) {
            sourceBackend.setTransitionFrameSyncEnabled(false, reason);
            if (workspaceSource && WorkstationProducerPolicy.shouldPauseSharedProducer(
                    true, MainHook.isWorkstationMode())
                    && !hasVisibleRecentsConsumer()) {
                if (sourceBackend.hasFreshFrame(sceneGeneration)) {
                    pauseWorkspaceProducerWhenFresh = false;
                    sourceBackend.setUpdatesEnabled(false, reason + "-settled");
                } else {
                    pauseWorkspaceProducerWhenFresh = true;
                }
            }
        }

        // Geometry-owned motion uses real pre-draw stability as its terminal boundary. Request
        // only the two settle observations it needs; a native spring lease has its own callbacks.
        if (transitionFrameSync.needsGeometrySettleFrame()
                && root != null && root.isAttachedToWindow()) {
            root.postInvalidateOnAnimation();
        }
    }

    private void recordUiPerf(long elapsedNs) {
        if (!MainHook.debugLogging) return;
        long now = System.nanoTime();
        if (uiPerfWindowStartNs == 0L) uiPerfWindowStartNs = now;
        uiPerfFrames++;
        uiPerfTotalNs += elapsedNs;
        uiPerfMaxNs = Math.max(uiPerfMaxNs, elapsedNs);
        if (now - uiPerfWindowStartNs < PERF_LOG_WINDOW_NS) return;
        long avgUs = uiPerfFrames > 0 ? uiPerfTotalNs / uiPerfFrames / 1_000L : 0L;
        MainHook.log("[DC][WorkspacePerf][UI] frames=" + uiPerfFrames
                + " avgSyncUs=" + avgUs
                + " maxSyncUs=" + (uiPerfMaxNs / 1_000L)
                + " hwuiProbeDraws=" + hwuiProbeDrawsWindow
                + " dragNodes=" + nodeStateSnapshot.length
                + " staticNodes=" + staticNodeStateSnapshot.length);
        uiPerfWindowStartNs = now;
        uiPerfFrames = 0;
        uiPerfTotalNs = 0L;
        uiPerfMaxNs = 0L;
        hwuiProbeDrawsWindow = 0;
    }

    void recordHwuiProbeDraw() {
        hwuiProbeDrawsWindow++;
    }

    private void recordGlPerf(long backdropNs, long sceneNs, long totalNs) {
        if (!MainHook.debugLogging) return;
        long now = System.nanoTime();
        if (glPerfWindowStartNs == 0L) glPerfWindowStartNs = now;
        glPerfFrames++;
        glPerfBackdropNs += backdropNs;
        glPerfSceneNs += sceneNs;
        glPerfTotalNs += totalNs;
        glPerfMaxNs = Math.max(glPerfMaxNs, totalNs);
        if (now - glPerfWindowStartNs < PERF_LOG_WINDOW_NS) return;
        long frames = Math.max(1, glPerfFrames);
        MainHook.log("[DC][WorkspacePerf][GL] frames=" + glPerfFrames
                + " avgBackdropUs=" + (glPerfBackdropNs / frames / 1_000L)
                + " avgSceneUs=" + (glPerfSceneNs / frames / 1_000L)
                + " avgTotalUs=" + (glPerfTotalNs / frames / 1_000L)
                + " maxTotalUs=" + (glPerfMaxNs / 1_000L)
                + " staticNodes=" + staticNodeStateSnapshot.length);
        glPerfWindowStartNs = now;
        glPerfFrames = 0;
        glPerfBackdropNs = 0L;
        glPerfSceneNs = 0L;
        glPerfTotalNs = 0L;
        glPerfMaxNs = 0L;
    }

    private void recoverFreshBackdropOnUi(long generation, int attempt) {
        // A frame request queued before native FolderCling opened must not
        // silently clear the producer pause after its owner changed.
        if (shuttingDown || folderFrameFrozen || generation != sceneGeneration) return;
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
            if (nextGeneration > 0L) {
                sceneGeneration = LauncherGlassHomePresentationHook.generationAfterEndpointChange(
                        sceneGeneration, nextGeneration, rotationSettlePending);
            }
            return;
        }
        if (generation != sceneGeneration) return;
        sourceBackend.requestFresh(
                generation,
                workspaceSource
                        && WorkstationProducerPolicy.shouldUseSingleFramePulse(
                                MainHook.isWorkstationMode()));
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
        if (shuttingDown || backend != sourceBackend || frame == null) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        // The Recents child uses this OES frame on the same GL thread, with its own geometry.
        // Never reject its frame because Workspace static-output geometry is temporarily stale
        // or a HOME-only Prismal parameter is unavailable during the Recents transition.
        RecentsCapsuleGlassSession child = recentsConsumer;
        if (child != null) child.onSharedSourceFrame(backend, frame);
        // Folder only freezes HOME's output, never Recents' independent output.
        if (folderFrameFrozen) return;
        // HOME generation/rotation constraints are separate from the Recents GPU consumer.
        if (frame.generation != sceneGeneration || rotationSettlePending) return;
        if (LauncherGlassSceneController.isRecentsCoveredByVendor()) return;

        PrismalParams params = prismalParams;
        if (params == null) return;
        OutputState currentOutput = staticOutput;
        int windowWidth = currentOutput != null ? currentOutput.width : 0;
        int windowHeight = currentOutput != null ? currentOutput.height : 0;
        String mismatch = WorkspaceFrameGeometryPolicy.mismatch(
                frame.logicalWidth, frame.logicalHeight, frame.rotation,
                rootWidth, rootHeight, configRotation, windowWidth, windowHeight);
        if (mismatch != null) {
            // The source and TextureView change dimensions on different threads. Never
            // overwrite current root geometry from a stale source callback or rasterize
            // the old logical coordinates into a newer window (perceived magnification).
            MainHook.log(TAG + " [WorkspaceGeometry] stale frame rejected reason=" + mismatch
                    + " frame=" + frame.logicalWidth + "x" + frame.logicalHeight
                    + " rot=" + frame.rotation
                    + " root=" + rootWidth + "x" + rootHeight
                    + " rot=" + configRotation
                    + " output=" + windowWidth + "x" + windowHeight
                    + " scene=" + frame.generation);
            long now = SystemClock.uptimeMillis();
            if (now - lastGeometryMismatchRefreshMs >= 180L) {
                lastGeometryMismatchRefreshMs = now;
                mainHandler.post(() -> {
                    if (shuttingDown || rootRef.get() != root || !root.isAttachedToWindow()) return;
                    // Reconcile the actual ViewRoot before obtaining another source buffer.
                    syncSceneOnUiThread();
                    LauncherGlassSceneController.requestFreshForRoot(root);
                });
            }
            return;
        }
        boolean trace = Trace.isEnabled() && workspaceSource;
        if (trace) Trace.beginSection("LD.Workspace.SourceFrame");
        try {
            boolean perf = MainHook.debugLogging;
            long perfStartNs = perf ? System.nanoTime() : 0L;
            long backdropStartNs = perfStartNs;
            ensureLauncherGl();
            // UI pre-draw owns rootWidth/rootHeight. Never regress them to capture-time
            // dimensions on this render-thread callback.
            sourceBackend.makePbufferCurrent();
            WallpaperFrameToken pendingWallpaperFrame =
                    peekWallpaperFrameToken(frame.generation);
            boolean probeGeometryChanged = hwuiProbeBackdropLogicalWidth != frame.logicalWidth
                    || hwuiProbeBackdropLogicalHeight != frame.logicalHeight
                    || hwuiProbeBackdropRotation != frame.rotation;
            boolean probeWallpaperChanged = pendingWallpaperFrame.generation >= 0L
                    && (hwuiProbeBackdropWallpaperGeneration != pendingWallpaperFrame.generation
                        || hwuiProbeBackdropWallpaperAuthoritative
                                != pendingWallpaperFrame.authoritative);
            boolean probeBackdropChanged = probeGeometryChanged
                    || hwuiProbeExportDirty || probeWallpaperChanged;
            if (trace) Trace.beginSection("LD.Workspace.Backdrop");
            try {
                prismalRenderer.prepareBackdrop(
                        frame.normalizedTextureId,
                        frame.physicalWidth,
                        frame.physicalHeight,
                        frame.logicalWidth,
                        frame.logicalHeight,
                        params);
            } finally {
                if (trace) Trace.endSection();
            }
            long backdropDoneNs = perf ? System.nanoTime() : 0L;
            backdropPrepared = true;
            if (BuildConfig.WORKSPACE_GLASS_HWUI_PROBE && workspaceSource
                    && probeBackdropChanged) {
                long exportStartNs = System.nanoTime();
                Bitmap rawBitmap = null;
                Bitmap blurredBitmap = null;
                if (trace) Trace.beginSection("LD.Workspace.HWUIBackdropExport");
                try {
                    // Export Prismal's prepared pair. Its source adapter normalizes the PassBlur
                    // FBO orientation before both the Gaussian blur and the GLES material sample;
                    // exporting frame.normalizedTextureId here bypassed that flip and inverted
                    // the sharp texture relative to the blur.
                    rawBitmap = backend.copyTextureToHardwareBitmap(
                            prismalRenderer.preparedSourceTexture(),
                            prismalRenderer.preparedSourceWidth(),
                            prismalRenderer.preparedSourceHeight());
                    blurredBitmap = backend.copyTextureToHardwareBitmap(
                            prismalRenderer.preparedBlurTexture(),
                            prismalRenderer.preparedBlurWidth(),
                            prismalRenderer.preparedBlurHeight());
                    Bitmap rawForUi = rawBitmap;
                    Bitmap blurForUi = blurredBitmap;
                    int logicalWidth = frame.logicalWidth;
                    int logicalHeight = frame.logicalHeight;
                    int rotation = frame.rotation;
                    long sceneAtExport = frame.generation;
                    long wallpaperAtExport = pendingWallpaperFrame.generation;
                    long exportSerial = ++hwuiProbeExportSerial;
                    // Publish callbacks can run on the UI thread before this GL method finishes.
                    // Reset the dirty bit before enqueue, so a rejected publish cannot be
                    // overwritten by a later render-thread assignment.
                    hwuiProbeExportDirty = false;
                    if (probeGeometryChanged) {
                        mainHandler.post(() -> {
                            if (isCurrentHwuiProbeExport(root, sceneAtExport, exportSerial,
                                    logicalWidth, logicalHeight, rotation, wallpaperAtExport)) {
                                LauncherGlassStaticLayer.markHwuiProbeBackdropPending(root);
                            }
                        });
                    }
                    if (!mainHandler.post(() -> {
                        if (!isCurrentHwuiProbeExport(root, sceneAtExport, exportSerial,
                                logicalWidth, logicalHeight, rotation, wallpaperAtExport)) {
                            rawForUi.recycle();
                            blurForUi.recycle();
                            if (hwuiProbeExportSerial == exportSerial) hwuiProbeExportDirty = true;
                            return;
                        }
                        if (!LauncherGlassStaticLayer.publishHwuiProbeBackdrop(
                                root, rawForUi, blurForUi,
                                logicalWidth, logicalHeight, rotation)) {
                            if (hwuiProbeExportSerial == exportSerial) hwuiProbeExportDirty = true;
                        }
                    })) {
                        throw new IllegalStateException("HWUI backdrop publish queue rejected");
                    }
                    hwuiProbeBackdropLogicalWidth = logicalWidth;
                    hwuiProbeBackdropLogicalHeight = logicalHeight;
                    hwuiProbeBackdropRotation = rotation;
                    if (wallpaperAtExport >= 0L) {
                        hwuiProbeBackdropWallpaperGeneration = wallpaperAtExport;
                        hwuiProbeBackdropWallpaperAuthoritative =
                                pendingWallpaperFrame.authoritative;
                    }
                    MainHook.log(TAG + " HWUI probe backdrops exported raw="
                            + rawBitmap.getWidth() + "x" + rawBitmap.getHeight()
                            + " blur=" + blurredBitmap.getWidth() + "x" + blurredBitmap.getHeight()
                            + " logical=" + logicalWidth + "x" + logicalHeight
                            + " rotation=" + rotation
                            + " generation=" + frame.generation
                            + " elapsedMs=" + ((System.nanoTime() - exportStartNs) / 1_000_000f));
                } catch (Throwable exportError) {
                    hwuiProbeExportDirty = true;
                    if (rawBitmap != null) rawBitmap.recycle();
                    if (blurredBitmap != null) blurredBitmap.recycle();
                    // Do not keep displaying an obsolete HWUI wallpaper indefinitely when
                    // its replacement could not be exported. The existing GL output remains
                    // the fallback until a later valid source frame can be exported.
                    long failedGeneration = frame.generation;
                    mainHandler.post(() -> {
                        if (!shuttingDown && rootRef.get() == root
                                && sceneGeneration == failedGeneration) {
                            LauncherGlassStaticLayer.markHwuiProbeBackdropPending(root);
                        }
                    });
                    MainHook.log(TAG + " HWUI probe backdrop export failed: " + exportError);
                } finally {
                    if (trace) Trace.endSection();
                }
            }
            outputRenderState.consumeForSourceRender();
            renderOutputs(true, true);
            if (perf) {
                long sceneDoneNs = System.nanoTime();
                recordGlPerf(
                        backdropDoneNs - backdropStartNs,
                        sceneDoneNs - backdropDoneNs,
                        sceneDoneNs - perfStartNs);
            }
            WallpaperFrameToken wallpaperFrame = takeWallpaperFrameToken(frame.generation);
            boolean renderedStaticOutput = staticOutput != null;
            long renderedGeneration = frame.generation;
            if (WorkstationProducerPolicy.shouldPauseAfterFrameConsumed(MainHook.isWorkstationMode())) {
                sourceBackend.setUpdatesEnabled(false, "launcher-workstation-frame-consumed");
            }
            if (workspaceSource) {
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
                        // Stop a HOME pulse only after a matching fresh OES frame was consumed.
                        // A timed four-VSync pause could otherwise starve a slow producer.
                        if (!transitionFrameSync.isActive()
                                && !hasVisibleRecentsConsumer()
                                && WorkstationProducerPolicy.shouldUseSingleFramePulse(
                                    MainHook.isWorkstationMode())) {
                            pauseWorkspaceProducerWhenFresh = false;
                            sourceBackend.setUpdatesEnabled(false,
                                    "workspace-settled-after-fresh");
                        }
                        if (renderedStaticOutput) {
                            LauncherGlassSceneController.onFreshFrameRendered(
                                    root,
                                    renderedGeneration,
                                    wallpaperFrame.generation,
                                    wallpaperFrame.authoritative);
                        }
                    });
                });
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " fresh Prismal render failed " + debugLabel() + ": " + error);
            throw error;
        } finally {
            if (trace) Trace.endSection();
        }
    }

    /** Reject queued HWUI images from a stale scene, rotation or wallpaper generation. */
    private boolean isCurrentHwuiProbeExport(
            View root, long generation, long serial,
            int width, int height, int rotation, long wallpaperGeneration) {
        return !shuttingDown && !folderFrameFrozen
                && rootRef.get() == root && root != null && root.isAttachedToWindow()
                && generation == sceneGeneration && serial == hwuiProbeExportSerial
                && width == rootWidth && height == rootHeight && rotation == configRotation
                && (wallpaperGeneration < 0L
                    || LauncherGlassSceneController.isWallpaperContentGenerationCurrent(
                            root, wallpaperGeneration));
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (shuttingDown || generation != sceneGeneration) return;
        MainHook.log(TAG + " source backend failed closed " + debugLabel()
                + " generation=" + generation + ": " + error);
        RecentsCapsuleGlassSession child = recentsConsumer;
        // Native source failure can recover when a fresh root endpoint appears; do not
        // irreversibly destroy the Recents consumer on a transient bind exhaustion.
        if (child != null) child.onSharedSourceUnavailable(error);
        Runnable listener = terminalFailureListener;
        if (listener != null) {
            mainHandler.post(() -> {
                if (!shuttingDown && terminalFailureListener == listener) listener.run();
            });
        }
    }

    private void ensureLauncherGl() {
        sourceBackend.makePbufferCurrent();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
            compositePositionLocation =
                    GLES20.glGetAttribLocation(compositeProgram, "aPosition");
            compositeUvLocation =
                    GLES20.glGetAttribLocation(compositeProgram, "aUv");
            if (compositePositionLocation < 0 || compositeUvLocation < 0) {
                throw new IllegalStateException("composite quad attribute unavailable");
            }
            compositeTextureLocation = requireUniform(compositeProgram, "uTexture");
            compositeCropRectLocation = requireUniform(compositeProgram, "uCropRect");
            GLES20.glUseProgram(compositeProgram);
            GLES20.glUniform1i(compositeTextureLocation, 0);
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
    }

    private void renderOutputs(boolean renderStatic, boolean renderDrag) {
        if (folderFrameFrozen) return;
        PrismalParams params = prismalParams;
        if (!backdropPrepared || params == null || rootWidth <= 0 || rootHeight <= 0) return;
        View root = rootRef.get();
        if (renderStatic && !LauncherGlassStaticLayer.isHwuiProbeActive(root)) {
            renderStaticScene(params);
        }
        if (renderDrag) renderDragOutputs(params);
    }

    /** Draw all live Workspace glass nodes into the same root ViewRoot frame. */
    void drawHwuiProbe(Canvas canvas, Paint paint,
                       android.graphics.RuntimeShader baselineShader,
                       android.graphics.RuntimeShader decoratedShader,
                       android.graphics.RuntimeShader motionDecoratedShader) {
        if (!BuildConfig.WORKSPACE_GLASS_HWUI_PROBE || shuttingDown || canvas == null
                || paint == null || baselineShader == null || decoratedShader == null
                || !canvas.isHardwareAccelerated()) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        StaticSceneSnapshot scene = publishedStaticScene;
        StaticNodeState[] snapshot = scene.nodes;
        if (snapshot.length == 0) return;
        PrismalParams params = prismalParams != null ? prismalParams : PrismalParams.builder().build();
        float density = root.getResources().getDisplayMetrics().density;
        android.graphics.RuntimeShader activeShader = null;
        int drawnNodes = 0;
        int decoratedNodes = 0;
        float lastCenterX = 0f;
        float lastCenterY = 0f;
        float lastWidth = 0f;
        float lastHeight = 0f;
        StringBuilder firstFrameNodes = hwuiProbeSelectionLogged ? null : new StringBuilder();
        for (int i = 0; i < snapshot.length; i++) {
            StaticNodeState state = snapshot[i];
            LauncherGlassStaticNode node = state.nodeRef.get();
            StaticGeometryFrame frame = scene.frames[i];
            if (node == null || frame == null) continue;
            float visibleAlpha = frame.outputAlpha;
            if (visibleAlpha <= 0.001f) continue;
            LauncherGlassGeometry.Snapshot geometry = node.captureGeometry(root);
            if (geometry == null || geometry.width <= 0f || geometry.height <= 0f) continue;

            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    node.nodeKind(), launcherHighlightProfile, largeSurfaceHighlightProfile)
                    .withOs4EdgeReplacingLegacyEdge();
            PrismalEdgeDecoration decoration = GlassEdgeEffects.current(edgeScope(node.nodeKind()));
            float minDimension = Math.min(geometry.width, geometry.height);
            boolean decorated = decoration.hasBacklit() || decoration.hasShadow();
            if (decorated) decoratedNodes++;
            boolean coreOpticalTransition = decorated
                    && workspaceMotionQualityActive && motionDecoratedShader != null;
            android.graphics.RuntimeShader shader = coreOpticalTransition
                    ? motionDecoratedShader : decorated ? decoratedShader : baselineShader;
            if (shader != activeShader) {
                paint.setShader(shader);
                activeShader = shader;
            }
            float exteriorGuardPx = decorated
                    ? decoration.extraGuardPx(density, minDimension) : 2f;

            shader.setFloatUniform("u_resolution", (float) rootWidth, (float) rootHeight);
            shader.setFloatUniform("u_glassSize", geometry.width, geometry.height);
            shader.setFloatUniform("u_cornerRadii",
                    geometry.cornerRadius, geometry.cornerRadius,
                    geometry.cornerRadius, geometry.cornerRadius);
            shader.setFloatUniform("uProbeBounds",
                    geometry.left, geometry.top,
                    geometry.left + geometry.width, geometry.top + geometry.height);
            setHwuiProbeMaterialUniforms(shader, params, highlights,
                    minDimension, hwuiProbeInvariants);
            if (decorated && !coreOpticalTransition) {
                setHwuiProbeDecorationUniforms(shader, decoration, density, minDimension);
            }
            PrismalInteractionState interaction = state.interaction;
            if (interaction != null) {
                shader.setFloatUniform("u_pressProgress", interaction.pressProgress);
                shader.setFloatUniform("u_glowCenter",
                        interaction.glowCenterX, interaction.glowCenterY);
            }
            paint.setAlpha(Math.round(255f * visibleAlpha));
            canvas.drawRect(
                    geometry.left - exteriorGuardPx,
                    geometry.top - exteriorGuardPx,
                    geometry.left + geometry.width + exteriorGuardPx,
                    geometry.top + geometry.height + exteriorGuardPx,
                    paint);

            if (firstFrameNodes != null) {
                if (firstFrameNodes.length() != 0) firstFrameNodes.append(',');
                View material = node.materialHost();
                firstFrameNodes.append(node.nodeKind()).append(':')
                        .append(material != null ? material.getClass().getSimpleName() : "detached")
                        .append('@').append(Math.round(geometry.left)).append(',')
                        .append(Math.round(geometry.top));
            }
            drawnNodes++;
            lastCenterX = geometry.centerX;
            lastCenterY = geometry.centerY;
            lastWidth = geometry.width;
            lastHeight = geometry.height;
        }
        paint.setShader(null);
        paint.setAlpha(255);
        if (firstFrameNodes != null) {
            MainHook.log(TAG + " HWUI probe drawing nodes=" + drawnNodes
                    + " of=" + snapshot.length + " visible=" + firstFrameNodes);
            android.util.Log.i("LiquidDock-HWUI", "drawing Workspace nodes=" + drawnNodes
                    + "/" + snapshot.length + " decorated=" + decoratedNodes);
            hwuiProbeSelectionLogged = true;
        }
        if (Trace.isEnabled()) {
            Integer scrollX = LauncherGlassStaticLayer.captureWorkspaceScrollAnchor(root);
            Trace.setCounter("LD.Workspace.HWUIProbe.Nodes", drawnNodes);
            Trace.setCounter("LD.Workspace.HWUIProbe.CenterX", Math.round(lastCenterX));
            Trace.setCounter("LD.Workspace.HWUIProbe.CenterY", Math.round(lastCenterY));
            Trace.setCounter("LD.Workspace.HWUIProbe.Width", Math.round(lastWidth));
            Trace.setCounter("LD.Workspace.HWUIProbe.Height", Math.round(lastHeight));
            Trace.setCounter("LD.Workspace.HWUIProbe.ScrollX",
                    scrollX != null ? scrollX : 0);
        }
    }

    static void setHwuiProbeDecorationUniforms(
            android.graphics.RuntimeShader shader,
            PrismalEdgeDecoration decoration,
            float density,
            float minDimension) {
        shader.setFloatUniform("u_backlitWidthPx", decoration.backlitWidthDp * density);
        shader.setFloatUniform("u_backlitStrength", decoration.backlitStrength);
        shader.setFloatUniform("u_backlitSideStart", decoration.backlitSideStart());
        shader.setFloatUniform("u_backlitSideSpan", decoration.backlitSideSpan());
        shader.setFloatUniform("u_backlitViewportInsetPx", decoration.inkViewportInsetPx);
        shader.setFloatUniform("u_castShadowRadiusPx",
                decoration.effectiveShadowRadiusPx(density, minDimension));
        shader.setFloatUniform("u_castShadowLightFollow", decoration.shadowLightFollow);
        shader.setFloatUniform("u_castShadowOffsetYPx",
                decoration.effectiveShadowOffsetYPx(density, minDimension));
        shader.setFloatUniform("u_castShadowColor",
                decoration.shadowR, decoration.shadowG, decoration.shadowB, decoration.shadowA);
    }

    static void setHwuiProbeMaterialUniforms(
            android.graphics.RuntimeShader shader,
            PrismalParams p,
            PrismalHighlightProfile h,
            float minGlassDimension,
            float[] invariants) {
        shader.setFloatUniform("u_refractionInset", p.refractionInsetPx);
        shader.setFloatUniform("u_sminSmoothing", p.sminSmoothingPx);
        shader.setFloatUniform("u_continuousCorners", p.continuousCorners ? 1f : 0f);
        shader.setFloatUniform("u_edgeRefractionFalloff", p.edgeRefractionFalloff);
        shader.setFloatUniform("u_ior", p.ior);
        shader.setFloatUniform("u_glassThickness", p.glassThicknessPx);
        shader.setFloatUniform("u_normalStrength", p.normalStrength);
        shader.setFloatUniform("u_displacementScale", p.displacementScale);
        shader.setFloatUniform("u_heightTransitionWidth", p.heightTransitionWidthPx);
        shader.setFloatUniform("u_lensRefractionPx",
                calculateLensRefractionPx(p, minGlassDimension));
        shader.setFloatUniform("u_lensDepthEffect", p.lensDepthEffect);
        shader.setFloatUniform("u_chromaticAberration", Math.max(0f, p.chromaticAberration));
        shader.setFloatUniform("u_dispersionR", p.dispersionR);
        shader.setFloatUniform("u_dispersionB", p.dispersionB);
        shader.setFloatUniform("u_vibrancy", p.vibrancy);
        shader.setFloatUniform("u_plainHighlight", p.plainHighlight);
        shader.setFloatUniform("u_liquidDome", p.liquidDome);
        shader.setFloatUniform("u_fresnelReflect", p.fresnelReflect);
        shader.setFloatUniform("u_brightness", p.brightness);
        shader.setFloatUniform("u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);
        shader.setFloatUniform("u_baseColor",
                p.baseColorR, p.baseColorG, p.baseColorB, p.baseColorA);
        shader.setFloatUniform("u_highlightWidth", p.highlightWidth);
        shader.setFloatUniform("u_os4EdgeWidthPx", p.os4EdgeWidthPx);
        shader.setFloatUniform("u_os4ReflectOffsetPx", p.os4ReflectOffsetPx);
        shader.setFloatUniform("u_os4ReflectionStrength", p.os4ReflectionStrength);
        shader.setFloatUniform("u_os4ReflectionLighten", p.os4ReflectionLighten);
        shader.setFloatUniform("u_os4DirectionalAngleRange", p.os4DirectionalAngleRange);
        shader.setFloatUniform("u_os4DirectionalIntensity", p.os4DirectionalIntensity);
        shader.setFloatUniform("u_os4DirectionalOppositeIntensity",
                p.os4DirectionalOppositeIntensity);
        shader.setFloatUniform("u_lightDir", p.lightDirX, p.lightDirY);
        shader.setFloatUniform("u_specular", p.specular);
        shader.setFloatUniform("u_shininess", p.shininess);
        shader.setFloatUniform("u_rimStrength", p.rimStrength);
        shader.setFloatUniform("u_shadowColor", p.shadowR, p.shadowG, p.shadowB, p.shadowA);
        shader.setFloatUniform("u_shadowSoftness", p.shadowSoftness);
        shader.setFloatUniform("u_causticIntensity", p.causticIntensity);
        shader.setFloatUniform("u_transmittance", p.transmittance);
        shader.setFloatUniform("u_backdropSampleScale", p.backdropScaleX, p.backdropScaleY);
        shader.setFloatUniform("u_parallaxScale", p.parallaxScale);
        shader.setFloatUniform("u_pressProgress", p.pressProgress);
        shader.setFloatUniform("u_backdropPinch", p.backdropPinch);
        shader.setFloatUniform("u_glowCenter", p.glowCenterX, p.glowCenterY);
        shader.setFloatUniform("u_glowStrength", p.glowStrength);
        shader.setIntUniform("u_showNormals", p.showNormals ? 1 : 0);
        shader.setIntUniform("u_useBlurredTexture", 1);

        PrismalRenderer.computeWorkspaceHwuiProbeInvariants(p, invariants);
        shader.setFloatUniform("u_invariantLightPrimary",
                invariants[0], invariants[1], invariants[2]);
        shader.setFloatUniform("u_invariantLightXY", invariants[3], invariants[4]);
        shader.setFloatUniform("u_invariantOs4LightDir",
                invariants[5], invariants[6], invariants[7]);
        shader.setFloatUniform("u_invariantFresnelR0", invariants[8]);
        shader.setFloatUniform("u_invariantShadowExtent", invariants[9]);

        shader.setFloatUniform("u_componentSkyHaze", h.skyHaze ? 1f : 0f);
        shader.setFloatUniform("u_componentSpecular", h.specular ? 1f : 0f);
        shader.setFloatUniform("u_componentLitRim", h.litRim ? 1f : 0f);
        shader.setFloatUniform("u_componentOppositeRim", h.oppositeRim ? 1f : 0f);
        shader.setFloatUniform("u_componentCornerRim", h.cornerRim ? 1f : 0f);
        shader.setFloatUniform("u_componentFaceSheen", h.faceSheen ? 1f : 0f);
        shader.setFloatUniform("u_componentPlainHighlight", h.plainHighlight ? 1f : 0f);
        shader.setFloatUniform("u_componentCaustics", h.caustics ? 1f : 0f);
        shader.setFloatUniform("u_componentPressGlow", h.pressGlow ? 1f : 0f);
        shader.setFloatUniform("u_os4EdgeEnabled", h.os4Edge ? 1f : 0f);

    }

    private static float calculateLensRefractionPx(PrismalParams p, float minGlassDimension) {
        float domeBoost = 1f + 0.55f * Math.max(0f, Math.min(2f, p.liquidDome));
        float refractionHeight = p.heightTransitionWidthPx * domeBoost;
        float lensPx = refractionHeight * 2f * p.displacementScale * p.lensRefractionScale
                * PrismalIorLensPolicy.relativeBend(p.ior);
        return Math.max(0f, Math.min(lensPx, Math.max(4f, minGlassDimension * 0.85f)));
    }

    private void renderStaticScene(PrismalParams params) {
        OutputState output = staticOutput;
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        // Background capture and blur remain downsampled, while procedural refraction
        // renders directly into the native-resolution window. The Prismal node scissor
        // limits expensive shading to occupied glass bounds.
        sourceBackend.makeCurrent(output.eglSurface);
        prismalRenderer.setTransientQualityActive(workspaceMotionQualityActive);
        prismalRenderer.beginGlassFrameOnSurface(output.width, output.height);
        StaticSceneSnapshot scene = publishedStaticScene;
        LauncherGlassScrollProjectionState.Snapshot scrollFrame =
                workspaceScrollProjection.snapshot();
        // Scope snapshots and display density are frame constants; resolving
        // them once avoids N repeated resources/Map lookups per draw cycle.
        View root = rootRef.get();
        float density = root != null
                ? root.getResources().getDisplayMetrics().density : 1f;
        PrismalEdgeDecoration iconShadow = GlassEdgeEffects.current(GlassEdgeEffects.ICON);
        PrismalEdgeDecoration widgetShadow = GlassEdgeEffects.current(GlassEdgeEffects.WIDGET);
        PrismalEdgeDecoration folderShadow = GlassEdgeEffects.current(GlassEdgeEffects.FOLDER);
        for (int i = 0; i < scene.nodes.length; i++) {
            StaticNodeState state = scene.nodes[i];
            LauncherGlassStaticNode node = state.nodeRef.get();
            StaticGeometryFrame frame = scene.frames[i];
            LauncherGlassGeometry.Snapshot geometry = frame != null ? frame.geometry : null;
            if (node == null || geometry == null) continue;
            // Invisible nodes must not consume an expensive GLES2 shader draw.
            // Their previous output is already removed by the scene clear.
            float visibleAlpha = frame.outputAlpha;
            if (visibleAlpha <= 0.001f) continue;
            float projectedCenterX = scrollFrame.projectCenterX(
                    geometry.centerX, frame.workspaceScrollX, frame.workspaceScrollValid);
            PrismalGeometry prismalGeometry = resolveStaticPrismalGeometry(
                    state, frame, geometry, projectedCenterX);
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    node.nodeKind(), launcherHighlightProfile, largeSurfaceHighlightProfile);
            LauncherGlassNodeKind kind = node.nodeKind();
            PrismalEdgeDecoration material = kind == LauncherGlassNodeKind.ICON
                    ? iconShadow : kind == LauncherGlassNodeKind.WIDGET
                    ? widgetShadow : folderShadow;
            prismalRenderer.setEdgeDecoration(material, density);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, highlights, state.interaction, visibleAlpha);
        }
        // A newer scroll or geometry revision supersedes this back buffer.
        // Its mutation has already requested a fresh render.
        if (!workspaceScrollProjection.isCurrent(scrollFrame)
                || publishedStaticScene != scene) {
            if (Trace.isEnabled() && workspaceSource) {
                Trace.setCounter("LD.Workspace.StaleStaticSwapDrops", ++staleStaticSwapDrops);
            }
            return;
        }
        sourceBackend.swapBuffers(output.eglSurface);
    }

    private PrismalGeometry resolveStaticPrismalGeometry(
            StaticNodeState state,
            StaticGeometryFrame frame,
            LauncherGlassGeometry.Snapshot geometry,
            float projectedCenterX) {
        PrismalGeometry cached = state.renderedGeometry;
        int width = rootWidth;
        int height = rootHeight;
        if (cached != null
                && state.renderedFrame == frame
                && state.renderedRootWidth == width
                && state.renderedRootHeight == height
                && Float.compare(state.renderedProjectedCenterX, projectedCenterX) == 0) {
            return cached;
        }
        PrismalGeometry created = new PrismalGeometry(
                width, height, projectedCenterX, geometry.centerY,
                geometry.width, geometry.height, geometry.cornerRadius);
        state.renderedFrame = frame;
        state.renderedRootWidth = width;
        state.renderedRootHeight = height;
        state.renderedProjectedCenterX = projectedCenterX;
        state.renderedGeometry = created;
        return created;
    }

    private static String edgeScope(LauncherGlassNodeKind kind) {
        return kind == LauncherGlassNodeKind.ICON
                ? GlassEdgeEffects.ICON
                : kind == LauncherGlassNodeKind.WIDGET
                ? GlassEdgeEffects.WIDGET : GlassEdgeEffects.FOLDER;
    }

    private void renderDragOutputs(PrismalParams params) {
        // Drag glass keeps its current full sampling path; this switch is Workspace app/HOME only.
        prismalRenderer.setTransientQualityActive(false);
        for (Map.Entry<LauncherGlassSinkView, OutputState> entry : outputs.entrySet()) {
            NodeState node = nodes.get(entry.getKey());
            LauncherGlassGeometry.Snapshot geometry = node != null ? node.geometry : null;
            if (node == null || geometry == null) continue;
            sourceBackend.makePbufferCurrent();
            // Drag outputs already use native-sized optical FBOs; backdrop sampling
            // quality is configured independently on RootPassBlurBackend.
            prismalRenderer.beginGlassFrame();
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            LauncherGlassSinkView sink = node.sinkRef.get();
            LauncherGlassNodeKind kind = sink != null
                    ? sink.nodeKind() : LauncherGlassNodeKind.LARGE_FOLDER;
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    kind, launcherHighlightProfile, largeSurfaceHighlightProfile);
            prismalRenderer.setEdgeDecoration(
                    sink != null ? GlassEdgeEffects.current(sink.opticalScope())
                            : GlassEdgeEffects.current(edgeScope(kind)),
                    rootRef.get() != null
                            ? rootRef.get().getResources().getDisplayMetrics().density : 1f);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, highlights, node.interaction);
            present(prismalRenderer.outputTexture(), geometry, entry.getValue());
        }
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
        bindQuad(compositePositionLocation, compositeUvLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
                GLES20.glUniform4f(compositeCropRectLocation,
                geometry.cropLeft, geometry.cropBottom, geometry.cropWidth, geometry.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositePositionLocation, compositeUvLocation);
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
        shutdown(null);
    }

    /** Source capture window remains attached until compositor detach + EGL release. */
    void shutdown(Runnable onShutdownComplete) {
        sourceBackend.onShutdownComplete(onShutdownComplete);
        if (shuttingDown) return;
        hwuiProbeExportSerial++;
        hwuiProbeExportDirty = true;
        MainHook.log(TAG + " shutdown " + debugLabel());
        RecentsCapsuleGlassSession child = recentsConsumer;
        recentsConsumer = null;
        if (child != null) child.onSharedSourceFailure(
                new IllegalStateException("Workspace source owner shut down"));
        WorkspaceTransitionFrameSyncState.Decision frameSyncReset = transitionFrameSync.reset();
        if (frameSyncReset.disable) {
            sourceBackend.setTransitionFrameSyncEnabled(false, "launcher-shutdown");
        }
        terminalFailureListener = null;
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
        boolean queued = sourceBackend.postShutdownToRenderThread(() -> {
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
        compositePositionLocation = compositeUvLocation = -1;
        compositeTextureLocation = compositeCropRectLocation = -1;
        backdropPrepared = false;
    }

    private void bindQuad(int position, int uv) {
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

    private void unbindQuad(int position, int uv) {
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
