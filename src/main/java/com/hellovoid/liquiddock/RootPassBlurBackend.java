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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Root-bound zero-copy PassBlur source backend shared by Launcher and Security Center domains.
 *
 * <p>The backend owns the authoritative ViewRoot/SurfaceControl endpoint, native producer Surface,
 * SurfaceTexture/OES input, source drain, normalization FBO, source freshness generation,
 * producer rollover, render thread and EGL context. Domain scene semantics and output ownership
 * remain in the caller.</p>
 */
final class RootPassBlurBackend {
    interface Consumer {
        /** Called on the backend render thread with the backend EGL context current. */
        void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame);
        void onTerminalFailure(long generation, Throwable error);
    }

    private static final String TAG = "[DC][RootPassBlur]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
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
        final int rotation;
        final SurfaceControl rootSurface;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        final int insetLeft;
        final int insetTop;
        final int insetRight;
        final int insetBottom;
        final RootPassBlurContentRect contentRect;

        ProducerGeometry(
                int surfaceWidth,
                int surfaceHeight,
                int bufferWidth,
                int bufferHeight,
                int rotation,
                SurfaceControl rootSurface,
                int viewRootIdentity,
                int surfaceSequenceId,
                int rootLayerId,
                int insetLeft,
                int insetTop,
                int insetRight,
                int insetBottom) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.rotation = rotation;
            this.rootSurface = rootSurface;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
            this.insetLeft = insetLeft;
            this.insetTop = insetTop;
            this.insetRight = insetRight;
            this.insetBottom = insetBottom;
            contentRect = RootPassBlurContentRect.resolve(
                    surfaceWidth, surfaceHeight, insetLeft, insetTop, insetRight, insetBottom);
        }
    }

    private final WeakReference<View> rootRef;
    private final PassBlurBindRequest bindRequest;
    private final Consumer consumer;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final Handler mainHandler;
    private final FloatBuffer quadBuffer;
    private final RootPassBlurBackendState state = new RootPassBlurBackendState();
    private final AtomicLong bindEpoch = new AtomicLong();
    private final float[] textureMatrix = new float[16];

    private volatile boolean shuttingDown;
    private volatile int physicalScalePercent;
    private volatile int renderFps;
    private volatile PassBlurSourceFrameGate sourceFrameGate;
    private volatile long sourceGeneration = -1L;
    private volatile long renderedGeneration = -1L;
    private volatile boolean requestedSingleFramePulse;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private volatile int bufferWidth;
    private volatile int bufferHeight;
    private volatile int rotation;
    private volatile RootPassBlurContentRect contentRect = RootPassBlurContentRect.full();
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile int maxTextureSize;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglPbufferSurface = EGL14.EGL_NO_SURFACE;
    private int normalizeProgram;
    private int oesTexture;
    private int normalizedTexture;
    private int normalizedFramebuffer;
    private int normalizedWidth;
    private int normalizedHeight;

    RootPassBlurBackend(
            View authoritativeRoot,
            PassBlurBindRequest bindRequest,
            int physicalScalePercent,
            int renderFps,
            Consumer consumer,
            String renderThreadName) {
        if (authoritativeRoot == null) {
            throw new IllegalArgumentException("authoritativeRoot == null");
        }
        if (bindRequest == null || bindRequest.host() != authoritativeRoot) {
            throw new IllegalArgumentException("bind request must target authoritative root");
        }
        rootRef = new WeakReference<>(authoritativeRoot);
        this.bindRequest = bindRequest;
        this.consumer = consumer;
        logicalWidth = Math.max(0, authoritativeRoot.getWidth());
        logicalHeight = Math.max(0, authoritativeRoot.getHeight());
        this.physicalScalePercent = physicalScalePercent;
        this.renderFps = renderFps;
        sourceFrameGate = new PassBlurSourceFrameGate(renderFps);
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
        String threadName = renderThreadName != null && !renderThreadName.isEmpty()
                ? renderThreadName : "LiquidDock-RootPassBlur-EGL";
        renderThread = new HandlerThread(threadName);
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        mainHandler = new Handler(authoritativeRoot.getContext().getMainLooper());

        postToRenderThread(() -> mainHandler.post(() -> {
            if (shuttingDown || rootRef.get() != authoritativeRoot) return;
            reconcileRoot();
            bindProducerWhenReady(0);
        }));
    }

    void requestFresh(long generation) {
        requestFresh(generation, false);
    }

    /** Launcher-only pulse variant; Workstation policy remains outside this backend. */
    void requestFresh(long generation, boolean singleFramePulse) {
        if (!state.requestFresh(generation) || shuttingDown) return;
        requestedSingleFramePulse = singleFramePulse;
        postToRenderThread(() -> {
            if (shuttingDown || state.requestedGeneration() != generation) return;
            // Render-queue barrier: OES callbacks already queued before requestFresh execute with
            // the previous sourceGeneration and cannot authorize the new generation.
            sourceGeneration = generation;
            mainHandler.post(() -> ensureBoundAndRefresh(generation));
        });
    }

    void setUpdatesEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        Miuix307PassBlurBridge.Binding current = binding;
        if (current == null) return;
        if (enabled) Miuix307PassBlurBridge.resumeUpdates(current);
        else Miuix307PassBlurBridge.pauseUpdates(current);
        MainHook.log(TAG + " updates=" + enabled + " reason=" + reason
                + " domain=" + bindRequest.domain());
    }

    void requestRebind(String reason) {
        if (shuttingDown || !renderThread.isAlive()) return;
        ZeroCopyProducerRecoveryState.Decision decision = state.requestRebind();
        if (!decision.accepted) return;
        long epoch = bindEpoch.incrementAndGet();
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        if (decision.clearFrameworkBinding) Miuix307PassBlurBridge.unbind(old);
        boolean queued = postToRenderThread(() -> {
            try {
                if (shuttingDown || epoch != bindEpoch.get()) return;
                releaseInputProducerEndpointOnRenderThread();
                if (shuttingDown || epoch != bindEpoch.get()) return;
                createInputProducer();
                ZeroCopyProducerRecoveryState.Decision recreated = state.onProducerRecreated();
                if (recreated.requestBind) mainHandler.post(() -> bindProducerWhenReady(0));
                MainHook.log(TAG + " producer recreated reason=" + reason
                        + " domain=" + bindRequest.domain());
            } catch (Throwable error) {
                state.onRecreateFailed();
                notifyTerminalFailure(error);
            }
        });
        if (!queued) {
            state.onRecreateFailed();
            notifyTerminalFailure(new IllegalStateException("render queue rejected rebind"));
        }
    }

    void setQuality(int physicalScalePercent, int renderFps) {
        if (shuttingDown) return;
        boolean scaleChanged = this.physicalScalePercent != physicalScalePercent;
        boolean fpsChanged = this.renderFps != renderFps;
        this.physicalScalePercent = physicalScalePercent;
        this.renderFps = renderFps;
        if (fpsChanged) sourceFrameGate = new PassBlurSourceFrameGate(renderFps);
        if (scaleChanged) {
            state.onQualityChanged();
            postToRenderThread(this::releaseNormalizedTarget);
        }
    }

    boolean hasFreshFrame(long generation) {
        return state.hasFreshFrame(generation);
    }

    boolean isRebindPending() {
        return state.isRebindPending();
    }

    boolean isActivationExhausted() {
        return state.isActivationExhausted();
    }

    boolean hasBinding() {
        Miuix307PassBlurBridge.Binding current = binding;
        return !shuttingDown && current != null && current.bound && current.rootSurface.isValid();
    }

    boolean postToRenderThread(Runnable runnable) {
        if (runnable == null || shuttingDown || !renderThread.isAlive()) return false;
        try {
            return renderHandler.post(() -> {
                if (shuttingDown) return;
                try {
                    ensureEglAndSource();
                    makePbufferCurrentUnchecked();
                } catch (Throwable sourceError) {
                    notifyTerminalFailure(sourceError);
                    return;
                }
                try {
                    runnable.run();
                } catch (Throwable callerError) {
                    MainHook.log(TAG + " render-thread consumer task failed: " + callerError);
                }
            });
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Main-thread endpoint/geometry reconciliation. Returns true only when source geometry or the
     * endpoint generation changed; caller-owned logical layout changes are intentionally separate.
     */
    boolean reconcileRoot() {
        if (shuttingDown) return false;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return false;
        int nextLogicalWidth = root.getWidth();
        int nextLogicalHeight = root.getHeight();
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            return false;
        }

        boolean logicalChanged = nextLogicalWidth > 0 && nextLogicalHeight > 0
                && (nextLogicalWidth != logicalWidth || nextLogicalHeight != logicalHeight);
        Miuix307PassBlurBridge.Binding current = binding;
        boolean endpointChanged = current != null && (!current.rootSurface.isValid()
                || !sameProducerSurfaceGeneration(current, geometry));
        RootPassBlurContentRect nextContentRect = geometry.contentRect;
        boolean sourceGeometryChanged = geometry.bufferWidth != bufferWidth
                || geometry.bufferHeight != bufferHeight
                || geometry.rotation != rotation
                || !nextContentRect.sameAs(contentRect);

        if (nextLogicalWidth > 0) logicalWidth = nextLogicalWidth;
        if (nextLogicalHeight > 0) logicalHeight = nextLogicalHeight;
        bufferWidth = geometry.bufferWidth;
        bufferHeight = geometry.bufferHeight;
        rotation = geometry.rotation;
        contentRect = nextContentRect;

        if (endpointChanged) {
            MainHook.log(TAG + " endpoint generation changed old=" + current.rootName
                    + " oldLayerId=" + current.rootLayerId
                    + " newLayerId=" + geometry.rootLayerId
                    + " oldSurfaceSeq=" + current.surfaceSequenceId
                    + " newSurfaceSeq=" + geometry.surfaceSequenceId);
            requestRebind("root-endpoint-changed");
            return true;
        }

        if (logicalChanged || sourceGeometryChanged) {
            state.onGeometryInvalidated();
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
                postToRenderThread(() -> {
                    if (shuttingDown || input != inputSurfaceTexture) return;
                    input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                    releaseNormalizedTarget();
                });
            }
        }
        return sourceGeometryChanged;
    }

    int currentRotation() {
        return rotation;
    }

    int logicalWidth() {
        return logicalWidth;
    }

    int logicalHeight() {
        return logicalHeight;
    }

    boolean isShutdown() {
        return shuttingDown;
    }

    EGLSurface createWindowSurface(Surface surface) {
        requireRenderThread();
        if (surface == null) return EGL14.EGL_NO_SURFACE;
        ensureEglAndSource();
        EGLSurface result = EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
        checkEgl("eglCreateWindowSurface", result != EGL14.EGL_NO_SURFACE);
        return result;
    }

    void destroyWindowSurface(EGLSurface surface) {
        requireRenderThread();
        if (surface == null || surface == EGL14.EGL_NO_SURFACE
                || eglDisplay == EGL14.EGL_NO_DISPLAY) return;
        EGL14.eglDestroySurface(eglDisplay, surface);
    }

    void makeCurrent(EGLSurface surface) {
        requireRenderThread();
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT
                || surface == null || surface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL surface unavailable");
        }
        checkEgl("eglMakeCurrent", EGL14.eglMakeCurrent(
                eglDisplay, surface, surface, eglContext));
    }

    void makePbufferCurrent() {
        requireRenderThread();
        makePbufferCurrentUnchecked();
    }

    void swapBuffers(EGLSurface surface) {
        requireRenderThread();
        if (!EGL14.eglSwapBuffers(eglDisplay, surface)) {
            throw new IllegalStateException("eglSwapBuffers error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    int maxTextureSize() {
        return maxTextureSize;
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        bindEpoch.incrementAndGet();
        state.onShutdown();
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        if (renderThread.isAlive()) {
            try {
                renderHandler.post(this::releaseGl);
            } catch (Throwable ignored) {
            }
            renderThread.quitSafely();
        }
    }

    private void ensureBoundAndRefresh(long generation) {
        if (shuttingDown || generation != state.requestedGeneration()) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        reconcileRoot();
        if (shuttingDown || generation != state.requestedGeneration()) return;
        Miuix307PassBlurBridge.Binding current = binding;
        if (current == null) {
            bindProducerWhenReady(0);
            return;
        }
        if (!current.bound || !current.rootSurface.isValid()) {
            requestRebind("fresh-request-invalid-binding");
            return;
        }
        if (requestedSingleFramePulse) {
            Miuix307PassBlurBridge.requestSingleUpdate(current, root);
        } else {
            Miuix307PassBlurBridge.resumeUpdates(current);
            root.postInvalidateOnAnimation();
        }
    }

    private void ensureEglAndSource() {
        ensureEgl();
        makePbufferCurrentUnchecked();
        if (normalizeProgram == 0) {
            normalizeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        }
        if (oesTexture == 0 || inputSurfaceTexture == null || inputProducerSurface == null) {
            createInputProducer();
        }
        if (maxTextureSize <= 0) {
            int[] size = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0);
            maxTextureSize = Math.max(1, size[0]);
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
        EGLContext context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreateContext", context != EGL14.EGL_NO_CONTEXT);
        EGLSurface pbuffer = EGL14.eglCreatePbufferSurface(
                display, configs[0],
                new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreatePbufferSurface", pbuffer != EGL14.EGL_NO_SURFACE);
        eglDisplay = display;
        eglConfig = configs[0];
        eglContext = context;
        eglPbufferSurface = pbuffer;
    }

    private void createInputProducer() {
        requireRenderThread();
        makePbufferCurrentUnchecked();
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
        if (bufferWidth > 0 && bufferHeight > 0) {
            input.setDefaultBufferSize(bufferWidth, bufferHeight);
        }
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        input.setOnFrameAvailableListener(this::onFrameAvailable, renderHandler);
        mainHandler.post(() -> bindProducerWhenReady(0));
    }

    private void onFrameAvailable(SurfaceTexture input) {
        if (shuttingDown || input == null || input != inputSurfaceTexture) return;
        long generation = sourceGeneration;
        PassBlurSourceFrameGate gate = sourceFrameGate;
        boolean shouldRender = generation >= 0L && (gate == null || gate.shouldSchedule(
                System.nanoTime(), renderedGeneration, generation));
        if (!shouldRender) {
            drainSourceFrameWithoutRender(input);
            return;
        }
        drainFreshFrame(input, generation);
    }

    private void drainSourceFrameWithoutRender(SurfaceTexture input) {
        if (shuttingDown || input == null || input != inputSurfaceTexture) return;
        try {
            makePbufferCurrentUnchecked();
            input.updateTexImage();
            input.getTransformMatrix(textureMatrix);
        } catch (Throwable error) {
            notifyTerminalFailure(error);
        }
    }

    private void drainFreshFrame(SurfaceTexture input, long generation) {
        if (shuttingDown || input == null || input != inputSurfaceTexture) return;
        try {
            makePbufferCurrentUnchecked();
            input.updateTexImage();
            input.getTransformMatrix(textureMatrix);
            if (generation < 0L || generation != sourceGeneration
                    || generation != state.requestedGeneration()) return;
            RootPassBlurFrame frame = normalizeFrame(generation);
            if (consumer != null) consumer.onFreshFrame(this, frame);
            if (generation == sourceGeneration && state.onFreshFrame(generation)) {
                renderedGeneration = generation;
            }
        } catch (Throwable error) {
            notifyTerminalFailure(error);
        }
    }

    private RootPassBlurFrame normalizeFrame(long generation) {
        int width = Math.max(1, logicalWidth);
        int height = Math.max(1, logicalHeight);
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(
                width, height, physicalScalePercent);
        ensureNormalizedTarget(domain.renderWidth, domain.renderHeight);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, normalizedFramebuffer);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, normalizedWidth, normalizedHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(requireUniform(normalizeProgram, "uTexMatrix"),
                1, false, textureMatrix, 0);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), rotation);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uValidDockRect"),
                0f, 0f, 1f, 1f);
        RootPassBlurContentRect rect = contentRect;
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"),
                rect.left, rect.bottom, rect.width, rect.height);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        return new RootPassBlurFrame(
                generation,
                normalizedTexture,
                domain.logicalWidth,
                domain.logicalHeight,
                domain.renderWidth,
                domain.renderHeight,
                rotation,
                rect);
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null) return;
        View root = rootRef.get();
        Surface producer = inputProducerSurface;
        SurfaceTexture input = inputSurfaceTexture;
        if (root == null || !root.isAttachedToWindow() || producer == null || input == null) {
            retryBind(attempt);
            return;
        }
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryBind(attempt);
            return;
        }
        logicalWidth = Math.max(1, root.getWidth());
        logicalHeight = Math.max(1, root.getHeight());
        bufferWidth = geometry.bufferWidth;
        bufferHeight = geometry.bufferHeight;
        rotation = geometry.rotation;
        contentRect = geometry.contentRect;
        long epoch = bindEpoch.get();
        postToRenderThread(() -> {
            if (shuttingDown || input != inputSurfaceTexture || epoch != bindEpoch.get()) return;
            input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
            mainHandler.post(() -> finishBind(root, producer, geometry, attempt, epoch));
        });
    }

    private void finishBind(
            View root, Surface producer, ProducerGeometry geometry, int attempt, long epoch) {
        if (shuttingDown || binding != null || producer != inputProducerSurface
                || epoch != bindEpoch.get() || rootRef.get() != root) return;
        Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(bindRequest, producer);
        if (next == null) {
            retryBind(attempt);
            return;
        }
        if (!sameProducerSurfaceGeneration(next, geometry)) {
            Miuix307PassBlurBridge.unbind(next);
            requestRebind("bind-endpoint-raced");
            return;
        }
        binding = next;
        state.onBindSucceeded();
        MainHook.log(TAG + " bound root=" + next.rootName
                + " domain=" + bindRequest.domain()
                + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                + " rotation=" + geometry.rotation);
        long generation = state.requestedGeneration();
        if (generation >= 0L) ensureBoundAndRefresh(generation);
    }

    private void retryBind(int attempt) {
        if (shuttingDown || binding != null) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            state.onBindExhausted();
            notifyTerminalFailure(new IllegalStateException("PassBlur bind exhausted"));
            return;
        }
        View root = rootRef.get();
        if (root != null) {
            root.postOnAnimation(() -> {
                if (!shuttingDown && binding == null) bindProducerWhenReady(attempt + 1);
            });
        }
    }

    private void ensureNormalizedTarget(int width, int height) {
        if (width <= 0 || height <= 0 || (maxTextureSize > 0
                && (width > maxTextureSize || height > maxTextureSize))) {
            throw new IllegalStateException("root PassBlur FBO size invalid " + width + "x" + height);
        }
        if (normalizedFramebuffer != 0 && normalizedWidth == width && normalizedHeight == height) {
            return;
        }
        releaseNormalizedTarget();
        normalizedTexture = createTexture2D(width, height);
        normalizedFramebuffer = createFramebuffer(normalizedTexture);
        normalizedWidth = width;
        normalizedHeight = height;
    }

    private void releaseNormalizedTarget() {
        requireRenderThread();
        if (normalizedFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{normalizedFramebuffer}, 0);
        }
        if (normalizedTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{normalizedTexture}, 0);
        }
        normalizedFramebuffer = 0;
        normalizedTexture = 0;
        normalizedWidth = 0;
        normalizedHeight = 0;
    }

    private void releaseInputProducerEndpointOnRenderThread() {
        requireRenderThread();
        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        SurfaceTexture input = inputSurfaceTexture;
        inputSurfaceTexture = null;
        if (input != null) {
            try { input.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}
        }
        if (producer != null) {
            try { producer.release(); } catch (Throwable ignored) {}
        }
        if (input != null) {
            try { input.release(); } catch (Throwable ignored) {}
        }
        if (oesTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
            oesTexture = 0;
        }
        releaseNormalizedTarget();
    }

    private void releaseGl() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglPbufferSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) makePbufferCurrentUnchecked();
        } catch (Throwable ignored) {}
        try { releaseInputProducerEndpointOnRenderThread(); } catch (Throwable ignored) {}
        if (normalizeProgram != 0) GLES20.glDeleteProgram(normalizeProgram);
        normalizeProgram = 0;
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

    private void notifyTerminalFailure(Throwable error) {
        if (shuttingDown) return;
        state.onTerminalFailure();
        long generation = state.requestedGeneration();
        MainHook.log(TAG + " terminal source failure generation=" + generation
                + " domain=" + bindRequest.domain() + ": " + error);
        if (consumer != null) {
            mainHandler.post(() -> {
                if (shuttingDown) return;
                try { consumer.onTerminalFailure(generation, error); }
                catch (Throwable ignored) {}
            });
        }
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
            int configRotation = readConfigRotation(root);
            int nextBufferWidth = surfaceWidth;
            int nextBufferHeight = surfaceHeight;
            if (configRotation == 1 || configRotation == 3) {
                nextBufferWidth = surfaceHeight;
                nextBufferHeight = surfaceWidth;
            }
            Method method = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            method.setAccessible(true);
            Object value = method.invoke(viewRoot);
            SurfaceControl surfaceControl = value instanceof SurfaceControl
                    ? (SurfaceControl) value : null;
            return new ProducerGeometry(
                    surfaceWidth, surfaceHeight, nextBufferWidth, nextBufferHeight,
                    configRotation, surfaceControl, System.identityHashCode(viewRoot),
                    Miuix307PassBlurBridge.readSurfaceSequenceId(viewRoot),
                    Miuix307PassBlurBridge.surfaceLayerId(surfaceControl),
                    surfaceInsets.left, surfaceInsets.top, surfaceInsets.right, surfaceInsets.bottom);
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
                && current.viewRootIdentity != geometry.viewRootIdentity) return false;
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
            throw new IllegalStateException("framebuffer incomplete=0x"
                    + Integer.toHexString(status));
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

    private void requireRenderThread() {
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException("root PassBlur GL access outside render thread");
        }
    }

    private void makePbufferCurrentUnchecked() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT
                || eglPbufferSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL pbuffer unavailable");
        }
        checkEgl("eglMakeCurrent", EGL14.eglMakeCurrent(
                eglDisplay, eglPbufferSurface, eglPbufferSurface, eglContext));
    }

    private static void checkEgl(String stage, boolean ok) {
        if (!ok) throw new IllegalStateException(stage + " error=0x"
                + Integer.toHexString(EGL14.eglGetError()));
    }
}
