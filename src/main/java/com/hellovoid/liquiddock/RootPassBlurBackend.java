package com.hellovoid.liquiddock;

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
import android.view.Surface;
import android.view.View;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Root-bound zero-copy PassBlur source backend shared by Launcher and Security Center domains.
 *
 * <p>The backend owns native producer Surface, SurfaceTexture/OES input, source drain,
 * normalization FBO, source freshness generation, producer rollover, render thread and EGL
 * lifecycle. System-private ViewRoot/SurfaceControl access is isolated in
 * {@link RootPassBlurEndpointBridge}; domain scene semantics and output ownership stay in callers.</p>
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
    private final Object rolloverCompletionLock = new Object();
    private final ArrayList<LauncherGlassSessionRegistry.RolloverCompletion> rolloverCompletions =
            new ArrayList<>();

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

    /** Launcher-only pulse selection; Workstation policy itself remains outside the backend. */
    void requestFresh(long generation, boolean singleFramePulse) {
        if (!state.requestFresh(generation) || shuttingDown) return;
        requestedSingleFramePulse = singleFramePulse;
        postToRenderThread(() -> {
            if (shuttingDown || state.requestedGeneration() != generation) return;
            // Render-queue barrier: source callbacks already queued before this request still carry
            // the previous generation and cannot authorize the new consumer generation.
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
        requestRebind(reason, null);
    }

    boolean requestRebind(
            String reason, LauncherGlassSessionRegistry.RolloverCompletion rolloverComplete) {
        if (shuttingDown || state.isShutdown() || !renderThread.isAlive()) {
            MainHook.log(TAG + " producer rebind rejected reason=" + reason
                    + " cause=shutdown domain=" + bindRequest.domain());
            return false;
        }
        ZeroCopyProducerRecoveryState.Decision decision = state.requestRebind();
        if (!decision.accepted) {
            if (state.isRebindPending() && rolloverComplete != null) {
                boolean attached = attachRolloverCompletion(rolloverComplete);
                MainHook.log(TAG + " producer rebind piggyback reason=" + reason
                        + " attached=" + attached + " domain=" + bindRequest.domain());
                return attached;
            }
            MainHook.log(TAG + " producer rebind rejected reason=" + reason
                    + " cause=recovery-state domain=" + bindRequest.domain());
            return false;
        }
        if (rolloverComplete != null && !attachRolloverCompletion(rolloverComplete)) {
            MainHook.log(TAG + " producer rebind rejected reason=" + reason
                    + " cause=completion-attach domain=" + bindRequest.domain());
            return false;
        }
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
        return queued;
    }

    boolean attachRolloverCompletion(
            LauncherGlassSessionRegistry.RolloverCompletion rolloverComplete) {
        if (rolloverComplete == null) return false;
        synchronized (rolloverCompletionLock) {
            if (shuttingDown || state.isShutdown() || !state.isRebindPending()) return false;
            rolloverCompletions.add(rolloverComplete);
        }
        MainHook.log(TAG + " rollover completion attached domain=" + bindRequest.domain());
        return true;
    }

    private void completeRolloverCompletions(boolean success, String reason) {
        List<LauncherGlassSessionRegistry.RolloverCompletion> callbacks;
        synchronized (rolloverCompletionLock) {
            if (rolloverCompletions.isEmpty()) return;
            callbacks = new ArrayList<>(rolloverCompletions);
            rolloverCompletions.clear();
        }
        MainHook.log(TAG + " rollover completion result=" + success
                + " reason=" + reason + " count=" + callbacks.size()
                + " domain=" + bindRequest.domain());
        mainHandler.post(() -> {
            for (LauncherGlassSessionRegistry.RolloverCompletion callback : callbacks) {
                try { callback.onComplete(success); }
                catch (Throwable error) {
                    MainHook.log(TAG + " rollover completion callback failed: " + error);
                }
            }
        });
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
        return !shuttingDown && RootPassBlurEndpointBridge.isBindingValid(binding);
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
     * Main-thread endpoint/geometry reconciliation. Returns true only for source geometry or
     * endpoint generation changes; caller-owned logical layout changes remain a domain concern.
     */
    boolean reconcileRoot() {
        if (shuttingDown) return false;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return false;
        RootPassBlurEndpointBridge.Endpoint endpoint = RootPassBlurEndpointBridge.inspect(root);
        if (endpoint == null || !endpoint.isValid()) return false;

        int nextLogicalWidth = root.getWidth();
        int nextLogicalHeight = root.getHeight();
        boolean logicalChanged = nextLogicalWidth > 0 && nextLogicalHeight > 0
                && (nextLogicalWidth != logicalWidth || nextLogicalHeight != logicalHeight);
        Miuix307PassBlurBridge.Binding current = binding;
        boolean endpointChanged = current != null
                && !RootPassBlurEndpointBridge.sameGeneration(current, endpoint);
        RootPassBlurContentRect nextContentRect = contentRect(endpoint);
        boolean sourceGeometryChanged = endpoint.bufferWidth != bufferWidth
                || endpoint.bufferHeight != bufferHeight
                || endpoint.rotation != rotation
                || !nextContentRect.sameAs(contentRect);

        if (nextLogicalWidth > 0) logicalWidth = nextLogicalWidth;
        if (nextLogicalHeight > 0) logicalHeight = nextLogicalHeight;
        bufferWidth = endpoint.bufferWidth;
        bufferHeight = endpoint.bufferHeight;
        rotation = endpoint.rotation;
        contentRect = nextContentRect;

        if (endpointChanged) {
            MainHook.log(TAG + " endpoint generation changed old=" + current.rootName
                    + " oldLayerId=" + current.rootLayerId
                    + " newLayerId=" + endpoint.rootLayerId
                    + " oldSurfaceSeq=" + current.surfaceSequenceId
                    + " newSurfaceSeq=" + endpoint.surfaceSequenceId);
            requestRebind("root-endpoint-changed");
            return true;
        }

        if (logicalChanged || sourceGeometryChanged) {
            state.onGeometryInvalidated();
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && endpoint.bufferWidth > 0 && endpoint.bufferHeight > 0) {
                postToRenderThread(() -> {
                    if (shuttingDown || input != inputSurfaceTexture) return;
                    input.setDefaultBufferSize(endpoint.bufferWidth, endpoint.bufferHeight);
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
        completeRolloverCompletions(false, "shutdown");
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        if (renderThread.isAlive()) {
            try { renderHandler.post(this::releaseGl); } catch (Throwable ignored) {}
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
        if (!RootPassBlurEndpointBridge.isBindingValid(current)) {
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
        if (bufferWidth > 0 && bufferHeight > 0) input.setDefaultBufferSize(bufferWidth, bufferHeight);
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
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(
                Math.max(1, logicalWidth), Math.max(1, logicalHeight), physicalScalePercent);
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
        RootPassBlurEndpointBridge.Endpoint endpoint = RootPassBlurEndpointBridge.inspect(root);
        if (endpoint == null || !endpoint.isValid()) {
            retryBind(attempt);
            return;
        }
        logicalWidth = Math.max(1, root.getWidth());
        logicalHeight = Math.max(1, root.getHeight());
        bufferWidth = endpoint.bufferWidth;
        bufferHeight = endpoint.bufferHeight;
        rotation = endpoint.rotation;
        contentRect = contentRect(endpoint);
        long epoch = bindEpoch.get();
        postToRenderThread(() -> {
            if (shuttingDown || input != inputSurfaceTexture || epoch != bindEpoch.get()) return;
            input.setDefaultBufferSize(endpoint.bufferWidth, endpoint.bufferHeight);
            mainHandler.post(() -> finishBind(root, producer, endpoint, attempt, epoch));
        });
    }

    private void finishBind(
            View root,
            Surface producer,
            RootPassBlurEndpointBridge.Endpoint endpoint,
            int attempt,
            long epoch) {
        if (shuttingDown || binding != null || producer != inputProducerSurface
                || epoch != bindEpoch.get() || rootRef.get() != root) return;
        Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(bindRequest, producer);
        if (next == null) {
            retryBind(attempt);
            return;
        }
        if (!RootPassBlurEndpointBridge.sameGeneration(next, endpoint)) {
            Miuix307PassBlurBridge.unbind(next);
            // The producer endpoint is still valid. Only the ViewRoot generation raced while
            // binding, so stay inside the accepted rollover and retry against the next frame's
            // authoritative endpoint instead of issuing a nested rebind that recovery rejects.
            retryBind(attempt);
            return;
        }
        binding = next;
        state.onBindSucceeded();
        completeRolloverCompletions(true, "bind-succeeded");
        MainHook.log(TAG + " bound root=" + next.rootName
                + " domain=" + bindRequest.domain()
                + " buffer=" + endpoint.bufferWidth + "x" + endpoint.bufferHeight
                + " rotation=" + endpoint.rotation);
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

    private RootPassBlurContentRect contentRect(RootPassBlurEndpointBridge.Endpoint endpoint) {
        return RootPassBlurContentRect.resolve(
                endpoint.surfaceWidth,
                endpoint.surfaceHeight,
                endpoint.insetLeft,
                endpoint.insetTop,
                endpoint.insetRight,
                endpoint.insetBottom);
    }

    private void ensureNormalizedTarget(int width, int height) {
        if (width <= 0 || height <= 0 || (maxTextureSize > 0
                && (width > maxTextureSize || height > maxTextureSize))) {
            throw new IllegalStateException("root PassBlur FBO size invalid " + width + "x" + height);
        }
        if (normalizedFramebuffer != 0 && normalizedWidth == width && normalizedHeight == height) return;
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
        String failureReason = error != null && error.getMessage() != null
                ? error.getMessage() : String.valueOf(error);
        completeRolloverCompletions(false, failureReason);
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
