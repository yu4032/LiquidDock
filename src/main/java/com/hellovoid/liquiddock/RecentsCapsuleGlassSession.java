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

/** Dedicated realtime Prismal scene shared by the two Recents action capsules. */
final class RecentsCapsuleGlassSession implements RootPassBlurBackend.Consumer {
    enum Target { CLEAR_ALL, WORLD }

    interface Listener {
        void onFirstFramePresented(Target target);
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][RecentsCapsule]";
    private static final long GENERATION = 1L;
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
    private final RootPassBlurBackend sourceBackend;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile launcherHighlightProfile;

    private volatile GeometrySet geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean clearAllPresentationSignaled;
    private boolean worldPresentationSignaled;
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
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
        int scalePercent = glassConfig != null
                ? glassConfig.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        int renderFps = glassConfig != null
                ? glassConfig.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        sourceBackend = new RootPassBlurBackend(
                sourceRoot,
                PassBlurBindRequest.recentsCapsule(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-RecentsCapsule-EGL");
    }

    void requestInitialCapture() {
        if (!shuttingDown) sourceBackend.requestFresh(GENERATION);
    }

    /**
     * Recents visibility is a real live-source authority. Workspace coverage may have toggled the
     * same root's SurfaceControl update flag off, so reassert continuous updates after the vendor
     * show boundary and request a generation-fenced frame before presenting new motion.
     */
    void onRecentsShown() {
        if (shuttingDown) return;
        sourceBackend.setUpdatesEnabled(true, "recents-capsule-visible");
        sourceBackend.requestFresh(GENERATION);
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

    @Override public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
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

    @Override public void onTerminalFailure(long generation, Throwable error) {
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
        GlQuadBindings.bind(quadBuffer, compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(GlProgramUtils.requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(GlProgramUtils.requireUniform(compositeProgram, "uCropRect"),
                g.cropLeft, g.cropBottom, g.cropWidth, g.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlQuadBindings.unbind(compositeProgram);
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
            sourceBackend.shutdown();
        });
        if (!queued) sourceBackend.shutdown();
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
            compositeProgram = GlProgramUtils.createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
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

                    }
