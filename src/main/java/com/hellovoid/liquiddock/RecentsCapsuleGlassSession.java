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

/** Two Recents Prismal outputs sharing the Workspace root producer and EGL render thread. */
final class RecentsCapsuleGlassSession {
    enum Target { CLEAR_ALL, WORLD }

    interface Listener {
        void onFirstFramePresented(Target target);
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][RecentsCapsule]";
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class OutputState {
        final Target target;
        final Surface surface;
        EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        int width;
        int height;
        OutputState(Target target, Surface surface, int width, int height) {
            this.target = target;
            this.surface = surface;
            this.width = width;
            this.height = height;
        }
    }

    static final class GeometrySet {
        final LauncherGlassGeometry.Snapshot clearAll;
        final LauncherGlassGeometry.Snapshot world;
        GeometrySet(LauncherGlassGeometry.Snapshot clearAll,
                    LauncherGlassGeometry.Snapshot world) {
            this.clearAll = clearAll;
            this.world = world;
        }
        boolean sameAs(GeometrySet other) {
            return other != null && same(clearAll, other.clearAll) && same(world, other.world);
        }
        private static boolean same(LauncherGlassGeometry.Snapshot a,
                                    LauncherGlassGeometry.Snapshot b) {
            return a == null ? b == null : a.sameAs(b);
        }
    }

    private final Handler mainHandler;
    private final Listener listener;
    private final LauncherGlassSession workspaceSourceOwner;
    private final RootPassBlurBackend sourceBackend;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile launcherHighlightProfile;

    private volatile GeometrySet geometry;
    private volatile boolean shuttingDown;
    private volatile boolean recentsVisible;
    private volatile boolean backdropPrepared;
    private boolean firstSharedFrameReported;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean clearAllPresentationSignaled;
    private boolean worldPresentationSignaled;
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private int compositePositionLocation = -1;
    private int compositeUvLocation = -1;
    private int compositeTextureLocation = -1;
    private int compositeCropRectLocation = -1;
    private OutputState clearAllOutput;
    private OutputState worldOutput;

    RecentsCapsuleGlassSession(View sourceRoot, LiquidDockConfig.Glass glassConfig,
                               Listener listener) {
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
        launcherHighlightProfile = glassConfig != null
                ? glassConfig.launcherHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        workspaceSourceOwner = LauncherGlassSessionRegistry.existingRootSource(sourceRoot);
        if (workspaceSourceOwner == null) {
            throw new IllegalStateException(
                    "No Workspace root source: keep vendor Recents capsule backgrounds");
        }
        sourceBackend = workspaceSourceOwner.attachRecentsConsumer(this);
        if (sourceBackend == null) {
            throw new IllegalStateException(
                    "Workspace source unavailable/busy: keep vendor Recents capsules");
        }
    }

    void requestInitialCapture() {
        // Sinks may attach before Recents is visible. Never start the root producer early.
        if (!shuttingDown && LauncherGlassSceneController.isRecentsCoveredByVendor()) {
            onRecentsShown();
        }
    }

    /** Resume the one Workspace-owned native source after Recents coverage pauses it. */
    void onRecentsShown() {
        if (shuttingDown) return;
        recentsVisible = true;
        firstSharedFrameReported = false;
        workspaceSourceOwner.resumeRecentsSharedSource(this);
    }

    boolean isRecentsVisible() {
        return recentsVisible && !shuttingDown;
    }

    void onRecentsHidden() {
        recentsVisible = false;
    }

    void updateGeometry(GeometrySet next) {
        if (shuttingDown || next == null) return;
        GeometrySet old = geometry;
        if (old != null && old.sameAs(next)) return;
        geometry = next;
        sourceBackend.postToRenderThread(this::renderCurrent);
    }

    void attachOutput(Target target, Surface surface, int width, int height) {
        if (target == null || surface == null || shuttingDown) {
            if (surface != null) surface.release();
            return;
        }
        if (!sourceBackend.postToRenderThread(() -> {
            if (shuttingDown) { surface.release(); return; }
            try {
                ensureGl();
                OutputState previous = outputFor(target);
                releaseOutput(previous);
                OutputState next = new OutputState(target, surface, width, height);
                next.eglSurface = sourceBackend.createWindowSurface(surface);
                setOutput(target, next);
                renderCurrent();
            } catch (Throwable error) {
                try { surface.release(); } catch (Throwable ignored) {}
                notifyFailure(error);
            }
        })) surface.release();
    }

    void resizeOutput(Target target, int width, int height) {
        if (target == null || shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            OutputState current = outputFor(target);
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
            renderCurrent();
        });
    }

    void detachOutput(Target target, Surface surface) {
        if (target == null || surface == null) return;
        if (shuttingDown || !sourceBackend.postToRenderThread(() -> {
            OutputState current = outputFor(target);
            if (current != null && current.surface == surface) {
                setOutput(target, null);
                releaseOutput(current);
            } else {
                try { surface.release(); } catch (Throwable ignored) {}
            }
        })) {
            try { surface.release(); } catch (Throwable ignored) {}
        }
    }

    /** Called only on the shared Workspace source render thread; normalizedTextureId is local. */
    void onSharedSourceFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || !recentsVisible || backend != sourceBackend || frame == null) return;
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
            if (MainHook.debugLogging && !firstSharedFrameReported) {
                firstSharedFrameReported = true;
                MainHook.log(TAG + " first shared frame rendered gen=" + frame.generation
                        + " size=" + frame.logicalWidth + "x" + frame.logicalHeight
                        + " outputs=" + (clearAllOutput != null) + "/"
                        + (worldOutput != null));
            }
        } catch (Throwable error) {
            notifyFailure(error);
        }
    }

    void onSharedSourceFailure(Throwable error) {
        if (!shuttingDown) notifyFailure(error);
    }

    private void renderCurrent() {
        GeometrySet currentGeometry = geometry;
        if (shuttingDown || !backdropPrepared || currentGeometry == null
                || logicalWidth <= 0 || logicalHeight <= 0) return;
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            drawGlass(currentGeometry.clearAll);
            drawGlass(currentGeometry.world);
            int sceneTexture = prismalRenderer.outputTexture();
            presentTarget(Target.CLEAR_ALL, sceneTexture, currentGeometry.clearAll, clearAllOutput);
            presentTarget(Target.WORLD, sceneTexture, currentGeometry.world, worldOutput);
        } catch (Throwable error) {
            notifyFailure(error);
        }
    }

    private void drawGlass(LauncherGlassGeometry.Snapshot g) {
        if (g == null) return;
        prismalRenderer.drawGlass(
                new PrismalGeometry(logicalWidth, logicalHeight, g.centerX, g.centerY,
                        g.width, g.height, g.cornerRadius),
                prismalParams,
                launcherHighlightProfile,
                PrismalInteractionState.IDLE);
    }

    private void presentTarget(Target target, int sceneTexture,
                               LauncherGlassGeometry.Snapshot g, OutputState current) {
        if (g == null || current == null || current.eglSurface == EGL14.EGL_NO_SURFACE) return;
        sourceBackend.makeCurrent(current.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, current.width, current.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositePositionLocation, compositeUvLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
                GLES20.glUniform4f(compositeCropRectLocation,
                g.cropLeft, g.cropBottom, g.cropWidth, g.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositePositionLocation, compositeUvLocation);
        sourceBackend.swapBuffers(current.eglSurface);
        signalPresented(target);
    }

    private void signalPresented(Target target) {
        if (target == Target.CLEAR_ALL) {
            if (clearAllPresentationSignaled) return;
            clearAllPresentationSignaled = true;
        } else {
            if (worldPresentationSignaled) return;
            worldPresentationSignaled = true;
        }
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFirstFramePresented(target);
        });
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        recentsVisible = false;
        workspaceSourceOwner.detachRecentsConsumer(this);
        boolean queued = sourceBackend.postToRenderThread(() -> {
            releaseOutput(clearAllOutput);
            releaseOutput(worldOutput);
            clearAllOutput = null;
            worldOutput = null;
            if (prismalRenderer != null) {
                try { prismalRenderer.close(); } catch (Throwable ignored) {}
                prismalRenderer = null;
            }
            if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
            compositePositionLocation = compositeUvLocation = -1;
            compositeTextureLocation = compositeCropRectLocation = -1;
            // Native root source is owned by Workspace and remains bound for HOME.
        });
        if (!queued) {
            // Owner may already be shutting down; never touch its EGL context on this thread.
            if (clearAllOutput != null) clearAllOutput.surface.release();
            if (worldOutput != null) worldOutput.surface.release();
            clearAllOutput = null;
            worldOutput = null;
        }
    }

    private OutputState outputFor(Target target) {
        return target == Target.CLEAR_ALL ? clearAllOutput : worldOutput;
    }

    private void setOutput(Target target, OutputState output) {
        if (target == Target.CLEAR_ALL) clearAllOutput = output;
        else worldOutput = output;
    }

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
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
    }

    private void releaseOutput(OutputState current) {
        if (current == null) return;
        if (current.eglSurface != EGL14.EGL_NO_SURFACE) {
            try { sourceBackend.destroyWindowSurface(current.eglSurface); } catch (Throwable ignored) {}
            current.eglSurface = EGL14.EGL_NO_SURFACE;
        }
        try { current.surface.release(); } catch (Throwable ignored) {}
    }

    private void notifyFailure(Throwable error) {
        MainHook.log(TAG + " Prismal failure: " + error);
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFailure(error);
        });
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
