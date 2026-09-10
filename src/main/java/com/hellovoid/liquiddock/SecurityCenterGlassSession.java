package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** One root-wide Security Center consumer of the generic PassBlur/OES backend. */
final class SecurityCenterGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        void onFrameRendered(SecurityCenterGlassSession session, long generation);
        void onTerminalFailure(
                SecurityCenterGlassSession session, long generation, Throwable error);
    }

    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class FrameRequest {
        final long serial;
        final long generation;
        final SecurityCenterGlassFrameGeometry frameGeometry;
        final SecurityCenterGlassSinkView[] sinks;

        FrameRequest(
                long serial,
                long generation,
                SecurityCenterGlassFrameGeometry frameGeometry,
                SecurityCenterGlassSinkView[] sinks) {
            this.serial = serial;
            this.generation = generation;
            this.frameGeometry = frameGeometry;
            this.sinks = sinks;
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

    private final WeakReference<View> rootRef;
    private final Listener listener;
    private final Handler mainHandler;
    private final FloatBuffer quadBuffer;
    private final RootPassBlurBackend sourceBackend;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;

    private volatile boolean shuttingDown;
    private volatile FrameRequest frameRequest;
    private volatile FrameRequest inFlight;
    private volatile OutputState[] inFlightOutputs;
    private long nextFrameSerial;
    private final SecurityCenterPresentationBarrier presentationBarrier =
            new SecurityCenterPresentationBarrier();

    // Backend render-thread only, except identity checks guarded by synchronizedMap on callback.
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private final Map<SecurityCenterGlassSinkView, OutputState> outputs =
            Collections.synchronizedMap(new WeakHashMap<>());

    SecurityCenterGlassSession(
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
        prismalParams = Miuix307PrismalAdapter.toPortable(optical);
        highlightProfile = glassConfig != null
                ? glassConfig.largeSurfaceHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        int scalePercent = glassConfig != null
                ? glassConfig.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        int renderFps = glassConfig != null
                ? glassConfig.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        sourceBackend = new RootPassBlurBackend(
                root,
                PassBlurBindRequest.securityCenter(root),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-SecurityCenterGlass-EGL");
        log("session created root=" + root.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(root)));
    }

    boolean ownsRoot(View root) {
        return root != null && rootRef.get() == root;
    }

    boolean isShutdown() {
        return shuttingDown;
    }

    void requestFresh(
            long generation,
            SecurityCenterGlassFrameGeometry frameGeometry,
            SecurityCenterGlassSinkView[] sinks) {
        if (shuttingDown || generation < 0L || frameGeometry == null || sinks == null
                || sinks.length != frameGeometry.nodeCount() || sinks.length == 0) return;
        View root = rootRef.get();
        SecurityCenterGlassGeometry presentation = frameGeometry.presentationGeometry();
        if (root == null || presentation.rootWidth != root.getWidth()
                || presentation.rootHeight != root.getHeight()) return;
        SecurityCenterGlassSinkView[] snapshot = sinks.clone();
        for (SecurityCenterGlassSinkView sink : snapshot) {
            if (sink == null || sink.isDisposed()) return;
        }
        frameRequest = new FrameRequest(++nextFrameSerial, generation, frameGeometry, snapshot);
        sourceBackend.requestFresh(generation);
    }

    void attachOutput(
            SecurityCenterGlassSinkView sink, Surface surface, int width, int height) {
        if (sink == null || surface == null) return;
        if (shuttingDown || !sourceBackend.postToRenderThread(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureGl();
                OutputState previous = outputs.remove(sink);
                releaseOutput(previous);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                outputs.put(sink, next);
                requestLatestFrameAfterOutputMutation();
            } catch (Throwable error) {
                surface.release();
                throw error;
            }
        })) {
            surface.release();
        }
    }

    void resizeOutput(SecurityCenterGlassSinkView sink, int width, int height) {
        if (sink == null || shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            OutputState current = outputs.get(sink);
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
            requestLatestFrameAfterOutputMutation();
        });
    }

    void detachOutput(SecurityCenterGlassSinkView sink, Surface surface) {
        if (surface == null) return;
        if (sink == null || shuttingDown || !sourceBackend.postToRenderThread(() -> {
            OutputState current = outputs.get(sink);
            if (current != null && current.surface == surface) {
                cancelInFlightIfUses(sink);
                outputs.remove(sink);
                releaseOutput(current);
            } else {
                surface.release();
            }
        })) {
            surface.release();
        }
    }

    private void requestLatestFrameAfterOutputMutation() {
        FrameRequest request = frameRequest;
        if (request == null) return;
        mainHandler.post(() -> {
            FrameRequest latest = frameRequest;
            if (!shuttingDown && latest != null
                    && latest.generation == request.generation) {
                sourceBackend.requestFresh(latest.generation);
            }
        });
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null || inFlight != null) return;
        FrameRequest request = frameRequest;
        View root = rootRef.get();
        SecurityCenterGlassGeometry presentation = request != null
                ? request.frameGeometry.presentationGeometry() : null;
        if (request == null || request.generation != frame.generation
                || presentation == null
                || root == null || !root.isAttachedToWindow()
                || presentation.rootWidth != frame.logicalWidth
                || presentation.rootHeight != frame.logicalHeight
                || request.sinks.length != request.frameGeometry.nodeCount()) {
            return;
        }

        OutputState[] requiredOutputs = new OutputState[request.sinks.length];
        for (int i = 0; i < request.sinks.length; i++) {
            SecurityCenterGlassSinkView sink = request.sinks[i];
            OutputState current = outputs.get(sink);
            if (sink == null || sink.isDisposed() || current == null
                    || current.eglSurface == EGL14.EGL_NO_SURFACE) return;
            requiredOutputs[i] = current;
        }

        inFlight = request;
        inFlightOutputs = requiredOutputs;
        presentationBarrier.begin(request.serial, request.sinks);
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            prismalRenderer.prepareBackdrop(
                    frame.normalizedTextureId,
                    frame.physicalWidth,
                    frame.physicalHeight,
                    frame.logicalWidth,
                    frame.logicalHeight,
                    prismalParams);

            for (int i = 0; i < request.frameGeometry.nodeCount(); i++) {
                SecurityCenterGlassGeometry geometry = request.frameGeometry.nodeAt(i);
                sourceBackend.makePbufferCurrent();
                prismalRenderer.beginGlassFrame();
                prismalRenderer.drawGlass(
                        geometry.toPrismalGeometry(),
                        prismalParams,
                        highlightProfile);
                request.sinks[i].armPresentation(request.serial, request.generation);
                presentTarget(prismalRenderer.outputTexture(), geometry, requiredOutputs[i]);
            }
            sourceBackend.makePbufferCurrent();

            log("render generation=" + frame.generation
                    + " nodes=" + request.frameGeometry.nodeCount()
                    + " root=" + frame.logicalWidth + "x" + frame.logicalHeight
                    + " physical=" + frame.physicalWidth + "x" + frame.physicalHeight);

            log("submitted serial=" + request.serial
                    + " generation=" + frame.generation
                    + " awaiting TextureView presentation ack");
        } catch (Throwable error) {
            for (SecurityCenterGlassSinkView sink : request.sinks) {
                if (sink != null) sink.clearPresentationArm(request.serial);
            }
            presentationBarrier.cancel(request.serial);
            if (inFlight == request) {
                inFlight = null;
                inFlightOutputs = null;
            }
            log("Prismal render failed generation=" + frame.generation + ": " + error);
            throw error;
        }
    }

    void onOutputPresented(
            SecurityCenterGlassSinkView sink, long serial, long generation) {
        if (shuttingDown || sink == null || serial < 0L || generation < 0L) return;
        FrameRequest active = inFlight;
        if (active == null || active.serial != serial || active.generation != generation
                || !presentationBarrier.isCurrent(serial)) return;
        if (!presentationBarrier.acknowledge(serial, sink)) return;

        presentationBarrier.cancel(serial);
        if (inFlight != active) return;
        OutputState[] expectedOutputs = inFlightOutputs;
        inFlight = null;
        inFlightOutputs = null;
        FrameRequest latest = frameRequest;
        boolean current = latest == active
                && outputsStillCurrent(active, expectedOutputs)
                && sourceBackend.hasFreshFrame(generation);
        if (current) {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onFrameRendered(this, generation);
            }
            log("presented serial=" + serial + " generation=" + generation);
        }
        if (!shuttingDown && latest != null && latest != active) {
            sourceBackend.requestFresh(latest.generation);
        }
    }

    private void cancelInFlightIfUses(SecurityCenterGlassSinkView sink) {
        FrameRequest active = inFlight;
        if (active == null || sink == null) return;
        boolean uses = false;
        for (SecurityCenterGlassSinkView candidate : active.sinks) {
            if (candidate == sink) { uses = true; break; }
        }
        if (!uses) return;
        for (SecurityCenterGlassSinkView candidate : active.sinks) {
            if (candidate != null) candidate.clearPresentationArm(active.serial);
        }
        presentationBarrier.cancel(active.serial);
        if (inFlight == active) {
            inFlight = null;
            inFlightOutputs = null;
        }
        requestLatestFrameAfterOutputMutation();
    }

    private boolean outputsStillCurrent(FrameRequest request, OutputState[] expected) {
        if (request == null || expected == null || request.sinks.length != expected.length) return false;
        for (int i = 0; i < request.sinks.length; i++) {
            if (outputs.get(request.sinks[i]) != expected[i]) return false;
        }
        return true;
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (shuttingDown) return;
        log("source terminal failure generation=" + generation + ": " + error);
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onTerminalFailure(this, generation, error);
        }
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        FrameRequest active = inFlight;
        inFlight = null;
        inFlightOutputs = null;
        if (active != null) {
            for (SecurityCenterGlassSinkView sink : active.sinks) {
                if (sink != null) sink.clearPresentationArm(active.serial);
            }
        }
        presentationBarrier.reset();
        frameRequest = null;
        log("session shutdown");
        boolean queued = sourceBackend.postToRenderThread(() -> {
            releaseGl();
            sourceBackend.shutdown();
        });
        if (!queued) sourceBackend.shutdown();
    }

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
    }

    private void presentTarget(
            int sceneTexture,
            SecurityCenterGlassGeometry geometry,
            OutputState current) {
        if (current == null || geometry == null
                || current.eglSurface == EGL14.EGL_NO_SURFACE
                || current.width <= 0 || current.height <= 0) return;
        float[] crop = geometry.toCropUvRect();
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
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(
                    "Security Center composite GLES error=0x" + Integer.toHexString(error));
        }
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

    private void releaseGl() {
        synchronized (outputs) {
            for (OutputState current : new ArrayList<>(outputs.values())) releaseOutput(current);
            outputs.clear();
        }
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
        compositeProgram = 0;
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

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
