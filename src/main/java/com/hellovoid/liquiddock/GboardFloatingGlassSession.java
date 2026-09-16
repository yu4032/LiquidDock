package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Continuous zero-copy PassBlur -> Prismal pipeline for one Gboard floating popup root. */
final class GboardFloatingGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        default void onFirstSwap() {}
        void onPresented();
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class OutputState {
        final Surface surface;
        EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        int width;
        int height;

        OutputState(Surface surface, int width, int height) {
            this.surface = surface;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
    }

    private final WeakReference<View> rootRef;
    private final Handler mainHandler;
    private final Listener listener;
    private final RootPassBlurBackend sourceBackend;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;
    private final GboardFloatingFrameRenderGate renderGate = new GboardFloatingFrameRenderGate();
    private final GboardFloatingDragSnapshotAuthority dragSnapshot =
            new GboardFloatingDragSnapshotAuthority();
    private final GboardFloatingLiveBackdropState liveBackdropState =
            new GboardFloatingLiveBackdropState();
    private final Object renderRevisionLock = new Object();

    private volatile GboardFloatingGlassGeometry geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean swapSucceeded;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private volatile long freshnessGeneration = 1L;
    private volatile long preparedBackdropGeneration = -1L;
    private long renderRevision;
    private boolean firstSwapSignaled;
    private boolean presentationSignaled;
    private boolean failureSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private OutputState output;

    GboardFloatingGlassSession(
            View root, LiquidDockConfig.Glass glassConfig, Listener listener) {
        if (root == null) throw new IllegalArgumentException("root == null");
        rootRef = new WeakReference<>(root);
        this.listener = listener;
        mainHandler = new Handler(root.getContext().getMainLooper());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        float density = root.getResources().getDisplayMetrics().density;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        PrismalParams baseParams = Miuix307PrismalAdapter.toPortable(optical);
        ThirdPartyGlassAppearance appearance =
                GboardGlassPreferences.resolveShared(ConfigReader.load(), glassConfig);
        prismalParams = ThirdPartyPrismalParams.apply(baseParams, appearance);
        highlightProfile = appearance.highlightProfile;
        sourceBackend = new RootPassBlurBackend(
                root,
                PassBlurBindRequest.gboardFloating(root),
                appearance.captureScalePercent,
                appearance.renderFps,
                this,
                "LiquidDock-GboardFloating-EGL");
    }

    void requestInitialCapture() {
        if (shuttingDown || !dragSnapshot.allowFreshRequest()) return;
        sourceBackend.reconcileRoot();
        sourceBackend.requestFresh(freshnessGeneration);
    }

    void beginDragSnapshot() {
        if (shuttingDown || !liveBackdropState.onDragStart(backdropPrepared)) return;
        GboardFloatingDragSnapshotAuthority.Decision decision =
                dragSnapshot.onDragStarted(backdropPrepared);
        if (decision.pauseLiveSource) {
            sourceBackend.setUpdatesEnabled(false, "gboard-drag-snapshot");
        }
    }

    void endDragSnapshot() {
        if (shuttingDown) return;
        GboardFloatingDragSnapshotAuthority.Decision decision = dragSnapshot.onDragEnded();
        if (!decision.resumeLiveSource && !decision.reconcileProducer
                && !decision.requestFreshBackdrop) return;
        long generation = ++freshnessGeneration;
        liveBackdropState.onDragEnd(generation);
        if (decision.resumeLiveSource) {
            sourceBackend.setUpdatesEnabled(true, "gboard-drag-release");
        }
        if (decision.reconcileProducer) {
            sourceBackend.reconcileRoot();
        }
        if (decision.requestFreshBackdrop) {
            sourceBackend.requestFresh(generation);
        }
    }

    /** Publish latest authoritative frame geometry. Geometry mutation is not producer lifecycle. */
    void updateGeometry(GboardFloatingGlassGeometry next) {
        if (shuttingDown || next == null || !dragSnapshot.allowGeometryRender()) return;
        GboardFloatingGlassGeometry old = geometry;
        if (old != null && old.sameAs(next)) return;
        if (old != null) {
            boolean liveRefreshAllowed = dragSnapshot.allowFreshRequest()
                    && liveBackdropState.phase() == GboardFloatingLiveBackdropState.Phase.LIVE;
            GboardFloatingRootRecoveryPolicy.Decision recovery =
                    GboardFloatingRootRecoveryPolicy.decide(
                            old.rootWidth, old.rootHeight,
                            next.rootWidth, next.rootHeight,
                            liveRefreshAllowed);
            if (recovery == GboardFloatingRootRecoveryPolicy.Decision.FAIL_CLOSED) {
                notifyFailure("root-size-changed-without-live-authority",
                        new IllegalStateException("root size changed while live refresh unavailable"));
                return;
            }
            geometry = next;
            if (recovery == GboardFloatingRootRecoveryPolicy.Decision.REQUEST_FRESH) {
                requestFreshForRootResize();
                return;
            }
        } else {
            geometry = next;
        }
        requestRender();
    }

    private void requestFreshForRootResize() {
        backdropPrepared = false;
        logicalWidth = 0;
        logicalHeight = 0;
        preparedBackdropGeneration = -1L;
        long generation = ++freshnessGeneration;
        sourceBackend.reconcileRoot();
        sourceBackend.requestFresh(generation);
    }

    void attachOutput(Surface surface, int width, int height) {
        if (surface == null || shuttingDown) {
            if (surface != null) surface.release();
            return;
        }
        if (!sourceBackend.postToRenderThread(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureGl();
                releaseOutput(output);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                output = next;
                requestRenderFromRenderThread();
            } catch (Throwable error) {
                surface.release();
                notifyFailure("output-attach", error);
            }
        })) surface.release();
    }

    void resizeOutput(int width, int height) {
        if (shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            OutputState current = output;
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
            requestRenderFromRenderThread();
        });
    }

    void detachOutput(Surface surface) {
        if (surface == null) return;
        if (shuttingDown || !sourceBackend.postToRenderThread(() -> {
            OutputState current = output;
            if (current != null && current.surface == surface) {
                output = null;
                releaseOutput(current);
            } else {
                surface.release();
            }
        })) surface.release();
    }

    /** Called on the UI thread only after the presentation backend consumed the swapped buffer. */
    void onOutputPresented() {
        if (shuttingDown || presentationSignaled || !swapSucceeded) return;
        presentationSignaled = true;
        if (listener != null) listener.onPresented();
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null
                || frame.generation != freshnessGeneration
                || !dragSnapshot.acceptLiveBackdrop()
                || !dragSnapshot.allowPrepareBackdrop()
                || !liveBackdropState.acceptLiveBackdrop()) return;
        try {
            ensureGl();
            logicalWidth = frame.logicalWidth;
            logicalHeight = frame.logicalHeight;
            sourceBackend.makePbufferCurrent();
            prismalRenderer.prepareBackdrop(
                    frame.normalizedTextureId,
                    frame.physicalWidth,
                    frame.physicalHeight,
                    frame.logicalWidth,
                    frame.logicalHeight,
                    prismalParams);
            backdropPrepared = true;
            preparedBackdropGeneration = frame.generation;
            liveBackdropState.onFreshBackdropPrepared(frame.generation);
            requestRenderFromRenderThread();
        } catch (Throwable error) {
            notifyFailure("fresh-frame", error);
        }
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (!shuttingDown) notifyFailure("source-terminal", error);
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        renderGate.cancel();
        boolean queued = sourceBackend.postToRenderThread(() -> {
            releaseOutput(output);
            output = null;
            if (prismalRenderer != null) {
                try { prismalRenderer.close(); } catch (Throwable ignored) {}
                prismalRenderer = null;
            }
            if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
            sourceBackend.shutdown();
        });
        if (!queued) sourceBackend.shutdown();
    }

    private long nextRenderRevision() {
        synchronized (renderRevisionLock) {
            return ++renderRevision;
        }
    }

    private void requestRender() {
        if (shuttingDown) return;
        long revision = nextRenderRevision();
        if (!renderGate.publish(revision)) return;
        if (!sourceBackend.postToRenderThread(this::drainRender)) {
            notifyFailure("render-queue", new IllegalStateException("render thread unavailable"));
        }
    }

    /** Used only when caller already owns the render thread. */
    private void requestRenderFromRenderThread() {
        if (shuttingDown) return;
        long revision = nextRenderRevision();
        if (renderGate.publish(revision)) drainRender();
    }

    private void drainRender() {
        long revision = renderGate.beginDrain();
        while (!shuttingDown && revision >= 0L) {
            renderCurrent();
            revision = renderGate.nextOrIdle();
        }
    }

    private void renderCurrent() {
        GboardFloatingGlassGeometry currentGeometry = geometry;
        OutputState currentOutput = output;
        View root = rootRef.get();
        if (shuttingDown || !backdropPrepared || currentGeometry == null
                || currentOutput == null || currentOutput.eglSurface == EGL14.EGL_NO_SURFACE
                || root == null || !root.isAttachedToWindow()
                || logicalWidth <= 0 || logicalHeight <= 0
                || currentGeometry.rootWidth != logicalWidth
                || currentGeometry.rootHeight != logicalHeight) return;
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            prismalRenderer.drawGlass(
                    currentGeometry.toPrismalGeometry(),
                    prismalParams,
                    highlightProfile,
                    PrismalInteractionState.IDLE);
            presentCropped(
                    prismalRenderer.outputTexture(),
                    currentGeometry.toCropUvRect(),
                    currentOutput);
            swapSucceeded = true;
            signalFirstSwap();
            liveBackdropState.onFreshOutputSwapped(preparedBackdropGeneration);
        } catch (Throwable error) {
            notifyFailure("render", error);
        }
    }

    private void signalFirstSwap() {
        if (firstSwapSignaled) return;
        firstSwapSignaled = true;
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFirstSwap();
        });
    }

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
    }

    private void presentCropped(int sceneTexture, float[] crop, OutputState current) {
        if (crop == null || crop.length != 4) return;
        sourceBackend.makeCurrent(current.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, current.width, current.height);
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
                crop[0], crop[1], crop[2], crop[3]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        sourceBackend.swapBuffers(current.eglSurface);
    }

    private void releaseOutput(OutputState current) {
        if (current == null) return;
        if (current.eglSurface != EGL14.EGL_NO_SURFACE) {
            try { sourceBackend.destroyWindowSurface(current.eglSurface); }
            catch (Throwable ignored) {}
            current.eglSurface = EGL14.EGL_NO_SURFACE;
        }
        try { current.surface.release(); } catch (Throwable ignored) {}
    }

    private void notifyFailure(String stage, Throwable error) {
        if (shuttingDown || failureSignaled) return;
        failureSignaled = true;
        try {
            Api101Bridge.log(TAG + " session failure stage=" + stage
                    + " cause=" + failureSummary(error));
        } catch (Throwable ignored) {}
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFailure(error);
        });
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
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
