package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One zero-copy PassBlur/OES/Prismal renderer for all glass nodes in one Launcher ViewRoot.
 * Sink views own only output surfaces; producer, EGL context and backdrop textures live here.
 */
final class LauncherGlassSession {
    interface ProducerRecoveryCompletion {
        void onComplete(LauncherGlassProducerRecoveryState.Result result);
    }

    private static final String TAG = "[DC][LauncherGlass]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
    private static final AtomicInteger NEXT_SESSION_ID = new AtomicInteger(1);
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class ProducerGeometry {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int configRotation;
        final SurfaceControl rootSurface;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        final int insetLeft;
        final int insetTop;
        final int insetRight;
        final int insetBottom;
        final LauncherGlassSurfaceContentRect contentRect;

        ProducerGeometry(
                int surfaceWidth, int surfaceHeight, int bufferWidth, int bufferHeight,
                int configRotation, SurfaceControl rootSurface,
                int viewRootIdentity, int surfaceSequenceId, int rootLayerId,
                int insetLeft, int insetTop, int insetRight, int insetBottom) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.configRotation = configRotation;
            this.rootSurface = rootSurface;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
            this.insetLeft = insetLeft;
            this.insetTop = insetTop;
            this.insetRight = insetRight;
            this.insetBottom = insetBottom;
            contentRect = LauncherGlassSurfaceContentRect.resolve(
                    surfaceWidth, surfaceHeight,
                    insetLeft, insetTop, insetRight, insetBottom);
        }
    }

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

    private static final class StaticNodeState {
        final WeakReference<LauncherGlassStaticNode> nodeRef;
        volatile LauncherGlassGeometry.Snapshot geometry;
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
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final Handler mainHandler;
    private final FloatBuffer quadBuffer;
    private final LauncherGlassFramePolicy framePolicy = new LauncherGlassFramePolicy();
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    // Transition epoch invalidates queued bind/frame callbacks before an endpoint-owner rollover.
    private volatile long workstationBindEpoch;
    // Producer endpoint identity. This is deliberately independent from sceneGeneration.
    private volatile long producerGeneration;
    private final LauncherGlassProducerRecoveryState workstationProducerRecovery =
            new LauncherGlassProducerRecoveryState();
    private volatile ProducerRecoveryCompletion workstationRecoveryCompletion;
    private volatile long workstationRecoveryCompletionSerial = -1L;
    private volatile String workstationRecoveryReason = "workstation-recents";
    private final float[] textureMatrix = new float[16];
    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LauncherGlassStaticNode, StaticNodeState> staticNodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    // EGL surfaces are created/destroyed only on renderHandler.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();
    private OutputState staticOutput;

    private volatile boolean shuttingDown;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile launcherHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile PrismalHighlightProfile largeSurfaceHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile int rootWidth;
    private volatile int rootHeight;
    private volatile int configRotation;
    private volatile int boundBufferWidth;
    private volatile int boundBufferHeight;
    private volatile LauncherGlassSurfaceContentRect contentRect =
            LauncherGlassSurfaceContentRect.full();
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile long sceneGeneration = 1L;
    private volatile long consumedGeneration = -1L;
    // Semantic content token for the one-shot producer pulse that requested a new wallpaper.
    // It is independent from scene generation and is consumed only by the matching OES frame.
    private long wallpaperRequestedGeneration = -1L;
    private long wallpaperRequestedSceneGeneration = -1L;
    private boolean wallpaperRequestedAuthoritative;
    // Display rotation reaches target ViewRoot geometry before Shell finishes its screenshot
    // rotation leash. While pending, no Workspace producer may bind or publish a fresh frame.
    private volatile long rotationSettleSerial;
    private volatile boolean rotationSettlePending;
    private volatile int rotationSettleTargetRotation = -1;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglPbufferSurface = EGL14.EGL_NO_SURFACE;
    private int normalizeProgram;
    private int compositeProgram;
    private int oesTexture;
    private int rawTexture;
    private int rawFramebuffer;
    private int rawWidth;
    private int rawHeight;
    private volatile int maxTextureSize;
    private PrismalRenderer prismalRenderer;
    private boolean backdropPrepared;
    private ViewTreeObserver rootObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private final View.OnAttachStateChangeListener rootAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { installRootObserver(); }
                @Override public void onViewDetachedFromWindow(View v) {
                    // Only the stable Launcher root owns the session. A folder/material reparent
                    // never reaches this listener. Defer one main-loop turn so transient root
                    // reattachment cannot kill a session that is already coming back.
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
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
        renderThread = new HandlerThread("LiquidDock-LauncherGlass-EGL");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        mainHandler = new Handler(root.getContext().getMainLooper());
        setGlassConfig(glassConfig);
        root.addOnAttachStateChangeListener(rootAttachListener);
        installRootObserver();
        MainHook.log(TAG + " " + debugLabel() + " created source=PassBlur-wallpaper-only");
    }

    boolean isShutdown() { return shuttingDown; }

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
        View root = rootRef.get();
        float density = root != null ? root.getResources().getDisplayMetrics().density : 1f;
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
        mainHandler.post(this::syncSceneOnUiThread);
        requestBackdropRebuild();
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
        // Interaction redraws reuse the last consumed wallpaper texture and prepared blur.
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
        invalidateBackdropFrameState();
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
        // A stable Launcher DecorView can survive while its ViewRoot/SurfaceControl is replaced
        // during App -> HOME. Revalidate on the UI thread before pulsing PassBlur so a fresh
        // request can never target a dead producer binding.
        mainHandler.post(() -> recoverFreshBackdropOnUi(generation, 0));
    }

    boolean requestWallpaperBackdrop(
            long sceneGeneration, long wallpaperGeneration, boolean authoritative) {
        if (shuttingDown || sceneGeneration != this.sceneGeneration || wallpaperGeneration < 0L
                || rotationSettlePending) {
            return false;
        }
        synchronized (this) {
            wallpaperRequestedGeneration = wallpaperGeneration;
            wallpaperRequestedSceneGeneration = sceneGeneration;
            wallpaperRequestedAuthoritative = authoritative;
        }
        // Keep the existing StaticLayer pixels visible while invalidating only the cached source.
        invalidateBackdropFrameState();
        requestFrame(true);
        return true;
    }

    void cancelWallpaperBackdrop(long wallpaperGeneration) {
        synchronized (this) {
            if (wallpaperRequestedGeneration != wallpaperGeneration) return;
            clearWallpaperRequestLocked();
        }
    }

    private void invalidateBackdropFrameState() {
        frameAvailable.set(false);
        consumedGeneration = -1L;
        backdropPrepared = false;
    }

    private void clearWallpaperRequest() {
        synchronized (this) {
            clearWallpaperRequestLocked();
        }
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
        if (shuttingDown) return;
        requestFrame(false);
    }

    private void recoverFreshBackdropOnUi(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }

        installRootObserver();
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }

        Miuix307PassBlurBridge.Binding current = binding;
        if (current != null && (!current.rootSurface.isValid()
                || !sameProducerSurfaceGeneration(current, geometry))) {
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName
                    + " oldLayerId=" + current.rootLayerId
                    + " new=" + geometry.rootSurface
                    + " newLayerId=" + geometry.rootLayerId
                    + " oldSurfaceSeq=" + current.surfaceSequenceId
                    + " newSurfaceSeq=" + geometry.surfaceSequenceId);
            rebindProducer();
            return;
        }

        boolean producerChanged = refreshProducerGeometryOnUi(root);
        if (producerChanged || generation != sceneGeneration) return;

        current = binding;
        if (current == null) {
            if (inputSurfaceTexture == null || inputProducerSurface == null) requestFrame(true);
            else bindProducerWhenReady(0);
            return;
        }
        if (!current.bound || !current.rootSurface.isValid()
                || !sameProducerSurfaceGeneration(current, geometry)) {
            rebindProducer();
            return;
        }
        requestFrame(true);
    }

    private void retryFreshBackdropRecovery(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration
                || attempt >= MAX_BIND_RETRY_FRAMES) return;
        View root = rootRef.get();
        if (root == null) return;
        root.postOnAnimation(() -> recoverFreshBackdropOnUi(generation, attempt + 1));
    }

    void requestDragRedraw() {
        if (shuttingDown) return;
        if (framePolicy.requestDrag()) postRender(this::drainFrameWork, null);
    }

    void requestStaticRedraw() {
        if (shuttingDown) return;
        if (framePolicy.requestStatic()) postRender(this::drainFrameWork, null);
    }

    void suspendWorkspaceProducer() {
        if (shuttingDown) return;
        if (WorkstationProducerPolicy.shouldPauseSharedProducer(
                true, MainHook.isWorkstationMode())) {
            Miuix307PassBlurBridge.pauseUpdates(binding);
        }
    }

    void attachOutput(LauncherGlassSinkView sink, Surface surface, int width, int height) {
        if (sink == null || surface == null) return;
        if (shuttingDown || !renderThread.isAlive()) {
            surface.release();
            return;
        }
        postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureEglAndGl();
                OutputState previous = outputs.remove(sink);
                releaseOutput(previous);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
                checkEgl("eglCreateWindowSurface", next.eglSurface != EGL14.EGL_NO_SURFACE);
                outputs.put(sink, next);
                requestFrame(false);
            } catch (Throwable error) {
                MainHook.log(TAG + " attach output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        }, surface::release);
    }

    void resizeOutput(LauncherGlassSinkView sink, int width, int height) {
        if (sink == null || shuttingDown || !renderThread.isAlive()) return;
        postRender(() -> {
            OutputState output = outputs.get(sink);
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                requestFrame(false);
            }
        }, null);
    }

    void detachOutput(LauncherGlassSinkView sink, Surface surface) {
        if (sink == null || shuttingDown || !renderThread.isAlive()) {
            if (surface != null) surface.release();
            return;
        }
        postRender(() -> {
            OutputState output = outputs.remove(sink);
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }, () -> { if (surface != null) surface.release(); });
    }

    void attachStaticOutput(Surface surface, int width, int height) {
        if (surface == null) return;
        if (shuttingDown || !renderThread.isAlive()) {
            surface.release();
            return;
        }
        postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureEglAndGl();
                releaseOutput(staticOutput);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
                checkEgl("eglCreateWindowSurface(static)", next.eglSurface != EGL14.EGL_NO_SURFACE);
                staticOutput = next;
                requestFrame(false);
                mainHandler.post(() ->
                        LauncherGlassSceneController.requestFreshForRoot(rootRef.get()));
            } catch (Throwable error) {
                MainHook.log(TAG + " attach static output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        }, surface::release);
    }

    void resizeStaticOutput(int width, int height) {
        if (shuttingDown || !renderThread.isAlive()) return;
        postRender(() -> {
            OutputState output = staticOutput;
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                requestFrame(false);
            }
        }, null);
    }

    void detachStaticOutput(Surface surface) {
        if (shuttingDown || !renderThread.isAlive()) {
            if (surface != null) surface.release();
            return;
        }
        postRender(() -> {
            OutputState output = staticOutput;
            staticOutput = null;
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }, () -> { if (surface != null) surface.release(); });
    }

    private void installRootObserver() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        ViewTreeObserver current = rootObserver;
        ViewTreeObserver.OnPreDrawListener currentListener = preDrawListener;
        if (current == observer && currentListener != null && observer.isAlive()) return;

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

        List<StaticNodeState> staticSnapshot;
        synchronized (staticNodes) { staticSnapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : staticSnapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            if (node == null) continue;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = state.geometry;
            if (observed == null && old != null && node.retainLastGeometryDuringFade()) continue;
            if ((old == null) != (observed == null)
                    || (old != null && !old.sameAs(observed))) {
                state.geometry = observed;
                staticChanged = true;
            }
        }

        boolean producerGeometryChanged = refreshProducerGeometryOnUi(root);
        if (producerGeometryChanged || rootGeometryChanged) {
            requestBackdropRebuild();
            return;
        }
        boolean schedule = false;
        if (staticChanged) schedule |= framePolicy.requestStatic();
        if (dragChanged) schedule |= framePolicy.requestDrag();
        if (schedule) postRender(this::drainFrameWork, null);
    }

    private boolean refreshProducerGeometryOnUi(View root) {
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            return false;
        }
        if (!LauncherGlassProducerGeometryGate.matchesRoot(
                rootWidth, rootHeight, geometry.surfaceWidth, geometry.surfaceHeight,
                geometry.insetLeft, geometry.insetTop, geometry.insetRight, geometry.insetBottom)) {
            invalidateBackdropFrameState();
            MainHook.log(TAG + " producer geometry not coherent with root root="
                    + rootWidth + "x" + rootHeight + " surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight);
            return false;
        }
        int previousRotation = configRotation;
        int nextRotation = geometry.configRotation;
        LauncherGlassSurfaceContentRect nextContentRect = geometry.contentRect;
        boolean rotationChanged = nextRotation != previousRotation;
        boolean geometryChanged = rotationChanged
                || geometry.bufferWidth != boundBufferWidth
                || geometry.bufferHeight != boundBufferHeight
                || !nextContentRect.sameAs(contentRect);
        Miuix307PassBlurBridge.Binding current = binding;
        boolean surfaceChanged = current != null && (!current.rootSurface.isValid()
                || !sameProducerSurfaceGeneration(current, geometry));
        boolean endpointRollover = LauncherGlassProducerTransitionPolicy.requiresEndpointRollover(
                previousRotation, nextRotation, surfaceChanged);
        boolean changed = geometryChanged || surfaceChanged;
        configRotation = nextRotation;
        if (!changed) return false;

        if (rotationChanged) beginRotationSettle(nextRotation);
        invalidateBackdropFrameState();
        long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
        if (nextGeneration > 0L) sceneGeneration = nextGeneration;
        boundBufferWidth = geometry.bufferWidth;
        boundBufferHeight = geometry.bufferHeight;
        contentRect = nextContentRect;

        if (surfaceChanged) {
            MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName
                    + " oldLayerId=" + current.rootLayerId
                    + " new=" + geometry.rootSurface
                    + " newLayerId=" + geometry.rootLayerId
                    + " oldSurfaceSeq=" + current.surfaceSequenceId
                    + " newSurfaceSeq=" + geometry.surfaceSequenceId);
        } else {
            MainHook.log(TAG + " producer geometry surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                    + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                    + " rotation=" + previousRotation + "->" + nextRotation
                    + " insets=" + geometry.insetLeft + "," + geometry.insetTop
                    + "," + geometry.insetRight + "," + geometry.insetBottom);
        }

        if (endpointRollover) {
            if (rotationChanged) {
                binding = null;
                Miuix307PassBlurBridge.unbind(current);
                scheduleRotationSettle(root, nextRotation);
            } else {
                rebindProducer();
            }
            return true;
        }

        SurfaceTexture input = inputSurfaceTexture;
        if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
            postRender(() -> {
                if (shuttingDown || input != inputSurfaceTexture) return;
                input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                if (geometryChanged && current != null && current.bound) {
                    mainHandler.post(() -> {
                        if (!shuttingDown && binding == current && current.bound
                                && input == inputSurfaceTexture) {
                            requestProducerRefresh(current, root);
                        }
                    });
                }
            }, null);
        }
        return true;
    }

    private void beginRotationSettle(int targetRotation) {
        workstationBindEpoch++;
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
        MainHook.log(TAG + " rotation capture released rotation=" + targetRotation
                + " serial=" + serial);
        long recoverySerial = workstationProducerRecovery.activeSerial();
        if (recoverySerial > 0L) {
            queueWorkstationEndpointRecreate(
                    workstationRecoveryReason, recoverySerial, false, "rotation-owner");
        } else {
            rebindProducer();
        }
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

    private boolean postRender(Runnable action, Runnable rejected) {
        if (action == null || shuttingDown || !renderThread.isAlive()) {
            if (rejected != null) rejected.run();
            return false;
        }
        try {
            boolean accepted = renderHandler.post(action);
            if (!accepted && rejected != null) rejected.run();
            return accepted;
        } catch (Throwable error) {
            MainHook.log(TAG + " render queue rejected " + debugLabel() + ": " + error);
            if (rejected != null) rejected.run();
            return false;
        }
    }

    private void requestFrame(boolean refreshProducer) {
        if (shuttingDown) return;
        if (framePolicy.request(refreshProducer)) postRender(this::drainFrameWork, null);
    }

    private void requestBackdropRebuild() {
        if (shuttingDown) return;
        if (framePolicy.requestBackdropRebuild()) postRender(this::drainFrameWork, null);
    }

    private void drainFrameWork() {
        if (shuttingDown) return;
        LauncherGlassFramePolicy.Work work = framePolicy.consume();
        if (!work.render) return;
        try {
            ensureEglAndGl();
            if (work.refreshProducer) refreshProducer();
            boolean sourceChanged = false;
            WallpaperFrameToken wallpaperFrame = WallpaperFrameToken.NONE;
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && frameAvailable.getAndSet(false)) {
                makePbufferCurrent();
                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                long consumedProducerGeneration = producerGeneration;
                consumedGeneration = sceneGeneration;
                wallpaperFrame = takeWallpaperFrameToken(consumedGeneration);
                sourceChanged = true;
                completeWorkstationRecoveryOnFreshFrame(consumedProducerGeneration);
                if (WorkstationProducerPolicy.shouldPauseSharedProducer(
                        true, MainHook.isWorkstationMode())) {
                    Miuix307PassBlurBridge.pauseUpdates(binding);
                }
            }
            if (consumedGeneration < 0L) return;
            boolean backdropDirty = work.rebuildBackdrop || sourceChanged || !backdropPrepared;
            boolean staticDirty = backdropDirty;
            if (work.staticDirty) staticDirty = true;
            boolean dragDirty = backdropDirty;
            if (work.dragDirty) dragDirty = true;
            renderScene(backdropDirty, staticDirty, dragDirty);
            long renderedGeneration = consumedGeneration;
            if (sourceChanged && staticOutput != null && renderedGeneration == sceneGeneration) {
                View rootView = rootRef.get();
                WallpaperFrameToken renderedWallpaperFrame = wallpaperFrame;
                mainHandler.post(() -> LauncherGlassSceneController.onFreshFrameRendered(
                        rootView, renderedGeneration,
                        renderedWallpaperFrame.generation, renderedWallpaperFrame.authoritative));
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " render failed: " + error);
            long recoverySerial = workstationProducerRecovery.activeSerial();
            if (recoverySerial > 0L) {
                failWorkstationRecovery(recoverySerial, "render", error);
            }
        }
    }

    private void refreshProducer() {
        if (rotationSettlePending) return;
        Miuix307PassBlurBridge.Binding current = binding;
        View root = rootRef.get();
        if (current == null || root == null || !current.bound || !current.rootSurface.isValid()) return;
        requestProducerRefresh(current, root);
    }

    private void requestProducerRefresh(
            Miuix307PassBlurBridge.Binding current, View root) {
        if (WorkstationProducerPolicy.shouldUseSingleFramePulse(
                MainHook.isWorkstationMode())) {
            Miuix307PassBlurBridge.requestSingleUpdate(current, root);
        } else {
            Miuix307PassBlurBridge.resumeUpdates(current);
            root.postInvalidateOnAnimation();
        }
    }

    private void ensureEglAndGl() {
        ensureEgl();
        makePbufferCurrent();
        if (normalizeProgram == 0) {
            normalizeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        }
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        if (prismalRenderer == null) {
            prismalRenderer = new PrismalRenderer();
            backdropPrepared = false;
        }
        boolean endpointMissing = oesTexture == 0
                || inputSurfaceTexture == null || inputProducerSurface == null;
        boolean explicitTransitionOwned = rotationSettlePending
                || workstationProducerRecovery.activeSerial() > 0L;
        if (endpointMissing
                && LauncherGlassProducerTransitionPolicy.canCreateProducerEndpoint(
                        explicitTransitionOwned)) {
            createInputProducer();
        }
        if (maxTextureSize <= 0) {
            int[] size = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0);
            maxTextureSize = Math.max(1, size[0]);
            mainHandler.post(this::syncSceneOnUiThread);
        }
    }

    private void ensureEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null && eglPbufferSurface != EGL14.EGL_NO_SURFACE) return;
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEgl("eglGetDisplay", display != EGL14.EGL_NO_DISPLAY);
        int[] version = new int[2];
        checkEgl("eglInitialize", EGL14.eglInitialize(display, version, 0, version, 1));
        int[] attrs = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        checkEgl("eglChooseConfig", EGL14.eglChooseConfig(
                display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0);
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreateContext", context != EGL14.EGL_NO_CONTEXT);
        EGLSurface pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{
                EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreatePbufferSurface", pbuffer != EGL14.EGL_NO_SURFACE);
        eglDisplay = display;
        eglConfig = configs[0];
        eglContext = context;
        eglPbufferSurface = pbuffer;
    }

    private void createInputProducer() {
        makePbufferCurrent();
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        oesTexture = textures[0];
        if (oesTexture == 0) throw new IllegalStateException("OES texture=0");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        SurfaceTexture input = new SurfaceTexture(oesTexture);
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        long endpointGeneration = ++producerGeneration;
        long endpointCallbackEpoch = workstationBindEpoch;
        backdropPrepared = false;

        long recoverySerial = workstationProducerRecovery.activeSerial();
        if (recoverySerial > 0L
                && workstationProducerRecovery.onEndpointRecreated(
                        recoverySerial, endpointGeneration)) {
            logWorkstationProducerRecovery(
                    workstationRecoveryReason, recoverySerial,
                    LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                    "endpoint-recreated", null);
        }

        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || rotationSettlePending || texture != inputSurfaceTexture
                    || !LauncherGlassProducerTransitionPolicy.isEndpointCallbackCurrent(
                            endpointCallbackEpoch, workstationBindEpoch)) {
                return;
            }
            frameAvailable.set(true);
            requestFrame(false);
        }, renderHandler);
        if (!mainHandler.post(() -> bindProducerWhenReady(0))
                && workstationProducerRecovery.isActive(recoverySerial)
                && workstationProducerRecovery.endpointGeneration() == endpointGeneration) {
            failWorkstationRecovery(recoverySerial, "bind-main-queue", null);
        }
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null || rotationSettlePending) return;
        long endpointGeneration = producerGeneration;
        long recoverySerial = workstationProducerRecovery.activeSerial();
        View root = rootRef.get();
        Surface producer = inputProducerSurface;
        SurfaceTexture input = inputSurfaceTexture;
        if (root == null || !root.isAttachedToWindow() || producer == null || input == null) {
            retryBind(attempt, endpointGeneration, recoverySerial);
            return;
        }
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryBind(attempt, endpointGeneration, recoverySerial);
            return;
        }
        long bindEpoch = workstationBindEpoch;
        boolean queued = postRender(() -> {
            if (shuttingDown || input != inputSurfaceTexture
                    || endpointGeneration != producerGeneration) return;
            input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
            boolean posted = mainHandler.post(() -> finishBind(
                    root, producer, geometry, attempt, bindEpoch,
                    endpointGeneration, recoverySerial));
            if (!posted && workstationProducerRecovery.isActive(recoverySerial)
                    && workstationProducerRecovery.endpointGeneration() == endpointGeneration) {
                failWorkstationRecovery(recoverySerial, "bind-main-queue", null);
            }
        }, null);
        if (!queued && workstationProducerRecovery.isActive(recoverySerial)
                && workstationProducerRecovery.endpointGeneration() == endpointGeneration) {
            failWorkstationRecovery(recoverySerial, "bind-render-queue", null);
        }
    }

    private void finishBind(
            View root, Surface producer, ProducerGeometry geometry, int attempt, long bindEpoch,
            long endpointGeneration, long recoverySerial) {
        if (shuttingDown || binding != null || rotationSettlePending
                || producer != inputProducerSurface
                || endpointGeneration != producerGeneration
                || bindEpoch != workstationBindEpoch) return;
        Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(root, producer, 1f);
        if (next == null) {
            retryBind(attempt, endpointGeneration, recoverySerial);
            return;
        }
        binding = next;
        if (WorkstationProducerPolicy.shouldPauseSharedProducer(
                LauncherGlassSceneController.isCoveredForRoot(root),
                MainHook.isWorkstationMode())) {
            Miuix307PassBlurBridge.pauseUpdates(next);
        }
        configRotation = geometry.configRotation;
        boundBufferWidth = geometry.bufferWidth;
        boundBufferHeight = geometry.bufferHeight;
        contentRect = geometry.contentRect;
        MainHook.log(TAG + " shared PassBlur producer bound " + debugLabel()
                + " surface=" + next.rootName + " buffer="
                + geometry.bufferWidth + "x" + geometry.bufferHeight
                + " insets=" + geometry.insetLeft + "," + geometry.insetTop
                + "," + geometry.insetRight + "," + geometry.insetBottom);

        if (workstationProducerRecovery.onBindSucceeded(recoverySerial, endpointGeneration)) {
            logWorkstationProducerRecovery(
                    workstationRecoveryReason, recoverySerial,
                    LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                    "bind-succeeded", null);
            scheduleWorkstationFreshFrameWatchdog(recoverySerial, endpointGeneration, 0);
        }
        requestFrame(true);
    }

    private void retryBind(int attempt, long endpointGeneration, long recoverySerial) {
        if (shuttingDown || binding != null || endpointGeneration != producerGeneration) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            if (workstationProducerRecovery.isActive(recoverySerial)
                    && workstationProducerRecovery.endpointGeneration() == endpointGeneration) {
                failWorkstationRecovery(recoverySerial, "bind-exhausted", null);
            }
            return;
        }
        View root = rootRef.get();
        if (root == null) {
            if (workstationProducerRecovery.isActive(recoverySerial)) {
                failWorkstationRecovery(recoverySerial, "bind-root-unavailable", null);
            }
            return;
        }
        root.postOnAnimation(() -> {
            if (shuttingDown || endpointGeneration != producerGeneration) return;
            bindProducerWhenReady(attempt + 1);
        });
    }

    private void scheduleWorkstationFreshFrameWatchdog(
            long recoverySerial, long endpointGeneration, int attempt) {
        if (!workstationProducerRecovery.isActive(recoverySerial)
                || workstationProducerRecovery.endpointGeneration() != endpointGeneration) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            failWorkstationRecovery(recoverySerial, "fresh-frame-exhausted", null);
            return;
        }
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) {
            failWorkstationRecovery(recoverySerial, "fresh-frame-root-unavailable", null);
            return;
        }
        root.postOnAnimation(() -> {
            if (!workstationProducerRecovery.isActive(recoverySerial)
                    || workstationProducerRecovery.endpointGeneration() != endpointGeneration) {
                return;
            }
            scheduleWorkstationFreshFrameWatchdog(
                    recoverySerial, endpointGeneration, attempt + 1);
        });
    }

    private void completeWorkstationRecoveryOnFreshFrame(long endpointGeneration) {
        long recoverySerial = workstationProducerRecovery.activeSerial();
        if (recoverySerial <= 0L) return;
        LauncherGlassProducerRecoveryState.Result terminal =
                workstationProducerRecovery.onFreshFrame(recoverySerial, endpointGeneration);
        if (terminal == null) return;
        dispatchWorkstationRecoveryTerminal(
                recoverySerial, terminal, "fresh-frame", null);
    }

    boolean suspendProducerForUnlockCapture() {
        if (shuttingDown) return false;
        Miuix307PassBlurBridge.Binding current = binding;
        if (current == null) return false;
        Miuix307PassBlurBridge.pauseUpdates(current);
        return true;
    }

    boolean rebindProducer() {
        return rebindProducer(null);
    }

    boolean rebindProducer(Runnable rolloverComplete) {
        if (shuttingDown || !renderThread.isAlive()) return false;
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        backdropPrepared = false;
        return postRender(() -> {
            if (shuttingDown) return;
            makePbufferCurrent();
            releaseInputProducerEndpointOnRenderThread();
            if (shuttingDown) return;
            MainHook.log(TAG + " rolling PassBlur producer endpoint " + debugLabel());
            createInputProducer();
            if (rolloverComplete != null) mainHandler.post(rolloverComplete);
        }, null);
    }

    LauncherGlassProducerRecoveryState.Result rebindWorkstationProducer(
            String reason, long recoverySerial, ProducerRecoveryCompletion completion) {
        String resolvedReason = reason != null ? reason : "workstation-recents";
        if (shuttingDown || !renderThread.isAlive()) {
            logWorkstationProducerRecovery(
                    resolvedReason, recoverySerial,
                    LauncherGlassProducerRecoveryState.Result.REJECTED,
                    "request", null);
            return LauncherGlassProducerRecoveryState.Result.REJECTED;
        }

        long previousSerial = workstationProducerRecovery.activeSerial();
        if (previousSerial > 0L && previousSerial != recoverySerial) {
            failWorkstationRecovery(previousSerial, "superseded-by-new-episode", null);
        }

        LauncherGlassProducerRecoveryState.Result request =
                workstationProducerRecovery.onRequest(recoverySerial);
        if (request != LauncherGlassProducerRecoveryState.Result.ACCEPTED) {
            logWorkstationProducerRecovery(
                    resolvedReason, recoverySerial, request, "request", null);
            return request;
        }

        synchronized (this) {
            workstationRecoveryCompletion = completion;
            workstationRecoveryCompletionSerial = recoverySerial;
            workstationRecoveryReason = resolvedReason;
        }
        logWorkstationProducerRecovery(
                resolvedReason, recoverySerial,
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                rotationSettlePending ? "request-rotation-owner" : "request", null);

        workstationBindEpoch++;
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        invalidateBackdropFrameState();
        clearWallpaperRequest();

        if (!LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(
                rotationSettlePending)) {
            return LauncherGlassProducerRecoveryState.Result.ACCEPTED;
        }
        return queueWorkstationEndpointRecreate(
                resolvedReason, recoverySerial, true, "workstation-owner");
    }

    private LauncherGlassProducerRecoveryState.Result queueWorkstationEndpointRecreate(
            String reason, long recoverySerial, boolean requestBoundary, String owner) {
        boolean queued = postRender(() -> {
            if (!workstationProducerRecovery.isActive(recoverySerial)) return;
            if (!LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(
                    rotationSettlePending)) {
                logWorkstationProducerRecovery(
                        reason, recoverySerial,
                        LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                        "deferred-to-rotation", null);
                return;
            }
            try {
                if (shuttingDown) {
                    failWorkstationRecovery(recoverySerial, "shutdown", null);
                    return;
                }
                makePbufferCurrent();
                releaseInputProducerEndpointOnRenderThread();
                if (!LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(
                        rotationSettlePending)) {
                    logWorkstationProducerRecovery(
                            reason, recoverySerial,
                            LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                            "deferred-to-rotation", null);
                    return;
                }
                if (shuttingDown) {
                    failWorkstationRecovery(recoverySerial, "shutdown", null);
                    return;
                }
                MainHook.log(TAG + " rolling PassBlur producer endpoint " + debugLabel()
                        + " owner=" + owner + " recoverySerial=" + recoverySerial);
                createInputProducer();
            } catch (Throwable error) {
                failWorkstationRecovery(recoverySerial, "endpoint-recreate", error);
            }
        }, null);

        if (queued) return LauncherGlassProducerRecoveryState.Result.ACCEPTED;
        if (requestBoundary) {
            LauncherGlassProducerRecoveryState.Result rejected =
                    workstationProducerRecovery.onRejected(recoverySerial);
            if (rejected != null) {
                logWorkstationProducerRecovery(
                        reason, recoverySerial, rejected, "request", null);
                clearWorkstationRecoveryCompletion(recoverySerial);
            }
            return LauncherGlassProducerRecoveryState.Result.REJECTED;
        }
        failWorkstationRecovery(recoverySerial, "endpoint-queue", null);
        return LauncherGlassProducerRecoveryState.Result.FAILED;
    }

    private void failWorkstationRecovery(long recoverySerial, String stage, Throwable error) {
        LauncherGlassProducerRecoveryState.Result failed =
                workstationProducerRecovery.onFailure(recoverySerial);
        if (failed == null) return;
        dispatchWorkstationRecoveryTerminal(recoverySerial, failed, stage, error);
    }

    private void dispatchWorkstationRecoveryTerminal(
            long recoverySerial, LauncherGlassProducerRecoveryState.Result result,
            String stage, Throwable error) {
        String reason = workstationRecoveryReason;
        logWorkstationProducerRecovery(reason, recoverySerial, result, stage, error);

        ProducerRecoveryCompletion completion = null;
        synchronized (this) {
            if (workstationRecoveryCompletionSerial == recoverySerial) {
                completion = workstationRecoveryCompletion;
                workstationRecoveryCompletion = null;
                workstationRecoveryCompletionSerial = -1L;
            }
        }
        if (completion == null) return;
        ProducerRecoveryCompletion terminalCompletion = completion;
        if (!mainHandler.post(() -> terminalCompletion.onComplete(result))) {
            terminalCompletion.onComplete(result);
        }
    }

    private void clearWorkstationRecoveryCompletion(long recoverySerial) {
        synchronized (this) {
            if (workstationRecoveryCompletionSerial != recoverySerial) return;
            workstationRecoveryCompletion = null;
            workstationRecoveryCompletionSerial = -1L;
        }
    }

    private void logWorkstationProducerRecovery(
            String reason, long recoverySerial,
            LauncherGlassProducerRecoveryState.Result result, String stage, Throwable error) {
        MainHook.log(TAG + "[ProducerRecovery] reason=" + reason
                + " session=" + diagnosticSessionId()
                + " producerGeneration=" + producerGeneration
                + " recoverySerial=" + recoverySerial
                + " result=" + result + " stage=" + stage
                + " endpointRecreated=" + workstationProducerRecovery.endpointRecreated()
                + " bindSucceeded=" + workstationProducerRecovery.bindSucceeded()
                + " freshFrameArrived=" + workstationProducerRecovery.freshFrameArrived()
                + (error != null ? " error=" + error : ""));
    }

    private void releaseInputProducerEndpointOnRenderThread() {
        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        SurfaceTexture input = inputSurfaceTexture;
        inputSurfaceTexture = null;

        invalidateBackdropFrameState();
        clearWallpaperRequest();

        if (input != null) {
            try { input.setOnFrameAvailableListener(null); }
            catch (Throwable ignored) {}
        }
        if (producer != null) {
            try { producer.release(); }
            catch (Throwable ignored) {}
        }
        if (input != null) {
            try { input.release(); }
            catch (Throwable ignored) {}
        }
        if (oesTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
            oesTexture = 0;
        }
    }

    private void renderScene(boolean rebuildBackdrop, boolean renderStatic, boolean renderDrag) {
        PrismalParams params = prismalParams;
        if (params == null || rootWidth <= 0 || rootHeight <= 0
                || (staticOutput == null && outputs.isEmpty())) return;
        makePbufferCurrent();
        boolean rawTargetChanged = rawFramebuffer == 0
                || rawWidth != rootWidth || rawHeight != rootHeight;
        ensureRawTarget(rootWidth, rootHeight);
        if (rebuildBackdrop || rawTargetChanged || !backdropPrepared) {
            renderNormalizationRoot();
            prismalRenderer.prepareBackdrop(rawTexture, rootWidth, rootHeight, params);
            backdropPrepared = true;
        }
        if (renderStatic) renderStaticScene(params);
        if (renderDrag) renderDragOutputs(params);
    }

    private void renderStaticScene(PrismalParams params) {
        OutputState output = staticOutput;
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE) return;
        makePbufferCurrent();
        prismalRenderer.beginGlassFrame();
        List<StaticNodeState> snapshot;
        synchronized (staticNodes) { snapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : snapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            LauncherGlassGeometry.Snapshot geometry = state.geometry;
            if (node == null || geometry == null) continue;
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    node.nodeKind(), launcherHighlightProfile, largeSurfaceHighlightProfile);
            prismalRenderer.drawGlass(prismalGeometry, params, highlights,
                    state.interaction, node.visibilityAlpha());
        }
        presentFull(prismalRenderer.outputTexture(), output);
    }

    private void renderDragOutputs(PrismalParams params) {
        for (Map.Entry<LauncherGlassSinkView, OutputState> entry
                : new ArrayList<>(outputs.entrySet())) {
            NodeState node = nodes.get(entry.getKey());
            LauncherGlassGeometry.Snapshot geometry = node != null ? node.geometry : null;
            if (geometry == null) continue;
            makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            LauncherGlassSinkView sink = node.sinkRef.get();
            LauncherGlassNodeKind kind = sink != null
                    ? sink.nodeKind() : LauncherGlassNodeKind.LARGE_FOLDER;
            PrismalHighlightProfile highlights = LauncherHighlightProfilePolicy.select(
                    kind, launcherHighlightProfile, largeSurfaceHighlightProfile);
            prismalRenderer.drawGlass(prismalGeometry, params, highlights, node.interaction);
            present(prismalRenderer.outputTexture(), geometry, entry.getValue());
        }
    }

    private void renderNormalizationRoot() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rawFramebuffer);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, rootWidth, rootHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(requireUniform(normalizeProgram, "uTexMatrix"),
                1, false, textureMatrix, 0);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), configRotation);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uValidDockRect"), 0f, 0f, 1f, 1f);
        LauncherGlassSurfaceContentRect contentRect = this.contentRect;
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"),
                contentRect.left, contentRect.bottom, contentRect.width, contentRect.height);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
    }

    private void presentFull(int sceneTexture, OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        makeCurrent(output.eglSurface);
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
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"), 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        if (!EGL14.eglSwapBuffers(eglDisplay, output.eglSurface)) {
            throw new IllegalStateException("eglSwapBuffers(static) error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void present(int sceneTexture, LauncherGlassGeometry.Snapshot geometry,
                         OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        makeCurrent(output.eglSurface);
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
        if (!EGL14.eglSwapBuffers(eglDisplay, output.eglSurface)) {
            throw new IllegalStateException("eglSwapBuffers error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void ensureRawTarget(int width, int height) {
        if (width <= 0 || height <= 0 || (maxTextureSize > 0
                && (width > maxTextureSize || height > maxTextureSize))) {
            throw new IllegalStateException("launcher glass FBO size invalid " + width + "x" + height);
        }
        if (rawFramebuffer != 0 && rawWidth == width && rawHeight == height) return;
        releaseRawTarget();
        rawTexture = createTexture2D(width, height);
        rawFramebuffer = createFramebuffer(rawTexture);
        rawWidth = width;
        rawHeight = height;
    }

    private void makePbufferCurrent() { makeCurrent(eglPbufferSurface); }

    private void makeCurrent(EGLSurface surface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT
                || surface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL surface unavailable");
        }
        checkEgl("eglMakeCurrent", EGL14.eglMakeCurrent(
                eglDisplay, surface, surface, eglContext));
    }

    private void releaseOutput(OutputState output) {
        if (output == null) return;
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && output.eglSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, output.eglSurface); } catch (Throwable ignored) {}
            output.eglSurface = EGL14.EGL_NO_SURFACE;
        }
        try { output.surface.release(); } catch (Throwable ignored) {}
    }

    private void releaseRawTarget() {
        if (rawFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{rawFramebuffer}, 0);
        if (rawTexture != 0) GLES20.glDeleteTextures(1, new int[]{rawTexture}, 0);
        rawFramebuffer = 0;
        rawTexture = 0;
        rawWidth = 0;
        rawHeight = 0;
        backdropPrepared = false;
    }

    void shutdown() {
        if (shuttingDown) return;
        long recoverySerial = workstationProducerRecovery.activeSerial();
        if (recoverySerial > 0L) {
            failWorkstationRecovery(recoverySerial, "shutdown", null);
        }
        MainHook.log(TAG + " shutdown " + debugLabel());
        shuttingDown = true;
        rotationSettleSerial++;
        rotationSettlePending = false;
        rotationSettleTargetRotation = -1;
        View root = rootRef.get();
        removeRootObserver();
        if (root != null) {
            try { root.removeOnAttachStateChangeListener(rootAttachListener); } catch (Throwable ignored) {}
            LauncherGlassSessionRegistry.forget(root, this);
        }
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        if (renderThread.isAlive()) {
            try {
                if (!renderHandler.post(this::releaseGl)) {
                    MainHook.log(TAG + " release queue rejected " + debugLabel());
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " release queue unavailable " + debugLabel() + ": " + error);
            }
            renderThread.quitSafely();
        }
    }

    private void releaseGl() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglPbufferSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) makePbufferCurrent();
        } catch (Throwable ignored) {}
        releaseOutput(staticOutput);
        staticOutput = null;
        for (OutputState output : new ArrayList<>(outputs.values())) releaseOutput(output);
        outputs.clear();
        try { releaseRawTarget(); } catch (Throwable ignored) {}
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (normalizeProgram != 0) GLES20.glDeleteProgram(normalizeProgram);
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
        normalizeProgram = compositeProgram = 0;
        releaseInputProducerEndpointOnRenderThread();
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglPbufferSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, eglPbufferSurface); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try { EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        }
        eglPbufferSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    private ProducerGeometry readSurfaceGeometry(View root) {
        try {
            Object viewRoot = getViewRootImpl(root);
            if (viewRoot == null) return null;
            Field sizeField = findField(viewRoot.getClass(), "mSurfaceSize");
            sizeField.setAccessible(true);
            Object sizeValue = sizeField.get(viewRoot);
            if (!(sizeValue instanceof Point)) return null;
            Point surfaceSize = (Point) sizeValue;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;
            Rect surfaceInsets = readSurfaceInsets(viewRoot);
            int rotation = readConfigRotation(root);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (rotation == 1 || rotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }
            Method method = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            method.setAccessible(true);
            Object value = method.invoke(viewRoot);
            SurfaceControl surfaceControl = value instanceof SurfaceControl
                    ? (SurfaceControl) value : null;
            int viewRootIdentity = System.identityHashCode(viewRoot);
            int surfaceSequenceId = Miuix307PassBlurBridge.readSurfaceSequenceId(viewRoot);
            int rootLayerId = Miuix307PassBlurBridge.surfaceLayerId(surfaceControl);
            return new ProducerGeometry(surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight, rotation, surfaceControl,
                    viewRootIdentity, surfaceSequenceId, rootLayerId,
                    surfaceInsets.left, surfaceInsets.top,
                    surfaceInsets.right, surfaceInsets.bottom);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rect readSurfaceInsets(Object viewRoot) {
        Rect result = new Rect();
        if (viewRoot == null) return result;
        try {
            Field attrsField = findField(viewRoot.getClass(), "mWindowAttributes");
            attrsField.setAccessible(true);
            Object attrs = attrsField.get(viewRoot);
            if (attrs == null) return result;
            Field insetsField = findField(attrs.getClass(), "surfaceInsets");
            insetsField.setAccessible(true);
            Object value = insetsField.get(attrs);
            if (value instanceof Rect) result.set((Rect) value);
        } catch (Throwable ignored) {}
        return result;
    }

    private static int readConfigRotation(View view) {
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

    private static Object getViewRootImpl(View view) throws Exception {
        Method method = View.class.getDeclaredMethod("getViewRootImpl");
        method.setAccessible(true);
        return method.invoke(view);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean sameProducerSurfaceGeneration(
            Miuix307PassBlurBridge.Binding current, ProducerGeometry geometry) {
        if (current == null || geometry == null) return false;
        if (current.viewRootIdentity != 0 && geometry.viewRootIdentity != 0
                && current.viewRootIdentity != geometry.viewRootIdentity) {
            return false;
        }
        boolean comparedImmutableGeneration = false;
        if (current.rootLayerId >= 0 && geometry.rootLayerId >= 0) {
            comparedImmutableGeneration = true;
            if (current.rootLayerId != geometry.rootLayerId) return false;
        }
        if (current.surfaceSequenceId >= 0 && geometry.surfaceSequenceId >= 0) {
            comparedImmutableGeneration = true;
            if (current.surfaceSequenceId != geometry.surfaceSequenceId) return false;
        }
        if (comparedImmutableGeneration) return true;
        return isSameSurface(current.rootSurface, geometry.rootSurface);
    }

    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            Method method = SurfaceControl.class.getMethod("isSameSurface", SurfaceControl.class);
            Object value = method.invoke(first, second);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return first.equals(second);
        }
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

    private static int createTexture2D(int width, int height) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int texture = ids[0];
        if (texture == 0) throw new IllegalStateException("texture=0");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer incomplete=0x" + Integer.toHexString(status));
        }
        return ids[0];
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

    private static void checkEgl(String stage, boolean ok) {
        if (!ok) throw new IllegalStateException(stage + " error=0x"
                + Integer.toHexString(EGL14.eglGetError()));
    }
}
