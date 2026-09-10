package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

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
        final long generation;
        final SecurityCenterGlassGeometry geometry;

        FrameRequest(long generation, SecurityCenterGlassGeometry geometry) {
            this.generation = generation;
            this.geometry = geometry;
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

    // Backend render-thread only.
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private OutputState output;

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
        MainHook.log(TAG + " session created root=" + root.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(root)));
    }

    boolean ownsRoot(View root) {
        return root != null && rootRef.get() == root;
    }

    boolean isShutdown() {
        return shuttingDown;
    }

    void requestFresh(long generation, SecurityCenterGlassGeometry geometry) {
        if (shuttingDown || generation < 0L || geometry == null) return;
        View root = rootRef.get();
        if (root == null || geometry.rootWidth != root.getWidth()
                || geometry.rootHeight != root.getHeight()) return;
        frameRequest = new FrameRequest(generation, geometry);
        sourceBackend.requestFresh(generation);
    }

    void attachOutput(Surface surface, int width, int height) {
        if (surface == null) return;
        if (shuttingDown || !sourceBackend.postToRenderThread(() -> {
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
                FrameRequest request = frameRequest;
                if (request != null) {
                    mainHandler.post(() -> {
                        if (!shuttingDown && frameRequest == request) {
                            sourceBackend.requestFresh(request.generation);
                        }
                    });
                }
            } catch (Throwable error) {
                surface.release();
                throw error;
            }
        })) {
            surface.release();
        }
    }

    void resizeOutput(int width, int height) {
        if (shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            OutputState current = output;
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
            FrameRequest request = frameRequest;
            if (request != null) {
                mainHandler.post(() -> {
                    if (!shuttingDown && frameRequest == request) {
                        sourceBackend.requestFresh(request.generation);
                    }
                });
            }
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
        })) {
            surface.release();
        }
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null) return;
        FrameRequest request = frameRequest;
        View root = rootRef.get();
        OutputState currentOutput = output;
        if (request == null || request.generation != frame.generation
                || root == null || !root.isAttachedToWindow()
                || request.geometry.rootWidth != frame.logicalWidth
                || request.geometry.rootHeight != frame.logicalHeight
                || currentOutput == null
                || currentOutput.eglSurface == EGL14.EGL_NO_SURFACE) {
            return;
        }

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
            prismalRenderer.beginGlassFrame();
            PrismalGeometry geometry = request.geometry.toPrismalGeometry();
            prismalRenderer.drawGlass(geometry, prismalParams, highlightProfile);
            presentFull(prismalRenderer.outputTexture(), currentOutput);
            sourceBackend.makePbufferCurrent();

            long renderedGeneration = frame.generation;
            sourceBackend.postToRenderThread(() -> {
                if (shuttingDown || frameRequest != request || output != currentOutput
                        || !sourceBackend.hasFreshFrame(renderedGeneration)) return;
                mainHandler.post(() -> {
                    if (shuttingDown || rootRef.get() != root || !root.isAttachedToWindow()
                            || frameRequest != request || output != currentOutput
                            || !sourceBackend.hasFreshFrame(renderedGeneration)) return;
                    Listener currentListener = listener;
                    if (currentListener != null) {
                        currentListener.onFrameRendered(
                                SecurityCenterGlassSession.this, renderedGeneration);
                    }
                });
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " Prismal render failed generation=" + frame.generation
                    + ": " + error);
            throw error;
        }
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (shuttingDown) return;
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onTerminalFailure(this, generation, error);
        }
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        frameRequest = null;
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

    private void presentFull(int sceneTexture, OutputState current) {
        if (current == null || current.eglSurface == EGL14.EGL_NO_SURFACE
                || current.width <= 0 || current.height <= 0) return;
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
                0f, 0f, 1f, 1f);
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
        releaseOutput(output);
        output = null;
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
}
