package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.EnumMap;

/** One zero-copy Prismal scene for the independent SystemUI app-handle popup window. */
final class SystemUiHandleMenuGlassSession implements RootPassBlurBackend.Consumer {
    enum Target {
        APP_INFO,
        WINDOWING,
        MORE_ACTIONS,
        OPEN_IN_APP
    }

    interface Listener {
        void onFirstFramePresented(Target target);
        void onFailure(Throwable error);
    }

    static final class GeometrySet {
        private final EnumMap<Target, LauncherGlassGeometry.Snapshot> values =
                new EnumMap<>(Target.class);

        void put(Target target, LauncherGlassGeometry.Snapshot geometry) {
            if (target != null) values.put(target, geometry);
        }

        LauncherGlassGeometry.Snapshot get(Target target) {
            return target != null ? values.get(target) : null;
        }

        boolean hasVisibleTarget() {
            for (Target target : Target.values()) {
                if (values.get(target) != null) return true;
            }
            return false;
        }

        boolean sameAs(GeometrySet other) {
            if (other == null) return false;
            for (Target target : Target.values()) {
                LauncherGlassGeometry.Snapshot a = values.get(target);
                LauncherGlassGeometry.Snapshot b = other.values.get(target);
                if (a == null ? b != null : !a.sameAs(b)) return false;
            }
            return true;
        }
    }

    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final long GENERATION = 1L;
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
            this.width = width;
            this.height = height;
        }
    }

    private final Handler mainHandler;
    private final Listener listener;
    private final RootPassBlurBackend sourceBackend;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;
    private final EnumMap<Target, OutputState> outputs = new EnumMap<>(Target.class);
    private final EnumMap<Target, Boolean> firstPresentation = new EnumMap<>(Target.class);

    private volatile GeometrySet geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;

    SystemUiHandleMenuGlassSession(
            View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener) {
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        this.listener = listener;
        mainHandler = new Handler(sourceRoot.getContext().getMainLooper());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        float density = sourceRoot.getResources().getDisplayMetrics().density;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        prismalParams = Miuix307PrismalAdapter.toPortable(optical);
        highlightProfile = glassConfig != null
                ? glassConfig.launcherHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        int scalePercent = glassConfig != null
                ? glassConfig.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        int renderFps = glassConfig != null
                ? glassConfig.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;

        sourceBackend = new RootPassBlurBackend(
                sourceRoot,
                PassBlurBindRequest.systemUiHandleMenu(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-SystemUiHandleMenu-EGL");
    }

    void requestInitialCapture() {
        if (!shuttingDown) sourceBackend.requestFresh(GENERATION);
    }

    void updateGeometry(GeometrySet next) {
        if (shuttingDown || next == null) return;
        GeometrySet previous = geometry;
        if (previous != null && previous.sameAs(next)) return;
        geometry = next;
        sourceBackend.postToRenderThread(this::renderCurrent);
    }

    void attachOutput(Target target, Surface surface, int width, int height) {
        if (target == null || surface == null || shuttingDown) {
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
                releaseOutput(outputs.remove(target));
                OutputState next = new OutputState(
                        surface, Math.max(1, width), Math.max(1, height));
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                outputs.put(target, next);
                renderCurrent();
            } catch (Throwable error) {
                try { surface.release(); } catch (Throwable ignored) {}
                notifyFailure(error);
            }
        })) {
            surface.release();
        }
    }

    void resizeOutput(Target target, int width, int height) {
        if (target == null || shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            OutputState current = outputs.get(target);
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
            renderCurrent();
        });
    }

    void detachOutput(Target target, Surface surface) {
        if (target == null || surface == null) return;
        if (shuttingDown || !sourceBackend.postToRenderThread(() -> {
            OutputState current = outputs.get(target);
            if (current != null && current.surface == surface) {
                outputs.remove(target);
                releaseOutput(current);
            } else {
                try { surface.release(); } catch (Throwable ignored) {}
            }
        })) {
            try { surface.release(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null
                || frame.generation != GENERATION) return;
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
            renderCurrent();
        } catch (Throwable error) {
            notifyFailure(error);
        }
    }

    @Override
    public void onTerminalFailure(long generation, Throwable error) {
        if (!shuttingDown && generation == GENERATION) notifyFailure(error);
    }

    private void renderCurrent() {
        GeometrySet currentGeometry = geometry;
        if (shuttingDown || !backdropPrepared || currentGeometry == null
                || logicalWidth <= 0 || logicalHeight <= 0) return;
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            for (Target target : Target.values()) {
                LauncherGlassGeometry.Snapshot value = currentGeometry.get(target);
                if (value != null) drawGlass(value);
            }
            int sceneTexture = prismalRenderer.outputTexture();
            for (Target target : Target.values()) {
                presentTarget(
                        target,
                        sceneTexture,
                        currentGeometry.get(target),
                        outputs.get(target));
            }
        } catch (Throwable error) {
            notifyFailure(error);
        }
    }

    private void drawGlass(LauncherGlassGeometry.Snapshot geometry) {
        prismalRenderer.drawGlass(
                new PrismalGeometry(
                        logicalWidth,
                        logicalHeight,
                        geometry.centerX,
                        geometry.centerY,
                        geometry.width,
                        geometry.height,
                        geometry.cornerRadius),
                prismalParams,
                highlightProfile,
                PrismalInteractionState.IDLE);
    }

    private void presentTarget(
            Target target,
            int sceneTexture,
            LauncherGlassGeometry.Snapshot geometry,
            OutputState current) {
        if (target == null || geometry == null || current == null
                || current.eglSurface == EGL14.EGL_NO_SURFACE) return;
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
        GLES20.glUniform4f(
                requireUniform(compositeProgram, "uCropRect"),
                geometry.cropLeft,
                geometry.cropBottom,
                geometry.cropWidth,
                geometry.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        sourceBackend.swapBuffers(current.eglSurface);
        signalPresented(target);
    }

    private void signalPresented(Target target) {
        if (Boolean.TRUE.equals(firstPresentation.get(target))) return;
        firstPresentation.put(target, Boolean.TRUE);
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFirstFramePresented(target);
        });
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        boolean queued = sourceBackend.postToRenderThread(() -> {
            for (OutputState current : outputs.values()) releaseOutput(current);
            outputs.clear();
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

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
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

    private void notifyFailure(Throwable error) {
        try { Api101Bridge.log(TAG + " Prismal failure: " + error); }
        catch (Throwable ignored) {}
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFailure(error);
        });
    }

    private void bindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) {
            throw new IllegalStateException("quad attribute unavailable");
        }
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(
                position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(
                uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
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
