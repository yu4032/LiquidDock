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

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** One-shot workspace backdrop capture plus stable full-screen rendering for ShortcutMenu glass. */
final class ShortcutPopupGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        void onPresented();
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][ShortcutPopupGlass]";
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

    private final WeakReference<View> sourceRootRef;
    private final Handler mainHandler;
    private final Listener listener;
    private final RootPassBlurBackend sourceBackend;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;

    private volatile LauncherGlassGeometry.Snapshot geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean sourceFrozen;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean presentationSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private OutputState output;

    ShortcutPopupGlassSession(
            View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener) {
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        sourceRootRef = new WeakReference<>(sourceRoot);
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
                ? glassConfig.largeSurfaceHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        int scalePercent = glassConfig != null
                ? glassConfig.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        int renderFps = glassConfig != null
                ? glassConfig.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        sourceBackend = new RootPassBlurBackend(
                sourceRoot,
                PassBlurBindRequest.shortcutPopup(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-ShortcutPopup-EGL");
    }

    void requestInitialCapture() {
        if (!shuttingDown) sourceBackend.requestFresh(GENERATION);
    }

    boolean hasFrozenBackdrop() {
        return !shuttingDown && backdropPrepared && sourceFrozen;
    }

    void updateGeometry(LauncherGlassGeometry.Snapshot next) {
        if (shuttingDown || next == null) return;
        LauncherGlassGeometry.Snapshot old = geometry;
        if (old != null && old.sameAs(next)) return;
        geometry = next;
        sourceBackend.postToRenderThread(this::renderCurrent);
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
                renderCurrent();
            } catch (Throwable error) {
                surface.release();
                notifyFailure(error);
            }
        })) surface.release();
    }

    void resizeOutput(int width, int height) {
        if (shuttingDown) return;
        sourceBackend.postToRenderThread(() -> {
            if (output == null) return;
            output.width = Math.max(1, width);
            output.height = Math.max(1, height);
            renderCurrent();
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
            if (!sourceFrozen) {
                sourceFrozen = true;
                sourceBackend.setUpdatesEnabled(false, "shortcut-popup-frozen");
                MainHook.log(TAG + " workspace backdrop frozen generation=" + frame.generation);
            }
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
        LauncherGlassGeometry.Snapshot currentGeometry = geometry;
        OutputState currentOutput = output;
        if (shuttingDown || !backdropPrepared || currentGeometry == null
                || currentOutput == null || currentOutput.eglSurface == EGL14.EGL_NO_SURFACE
                || logicalWidth <= 0 || logicalHeight <= 0) return;
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            prismalRenderer.drawGlass(
                    new PrismalGeometry(
                            logicalWidth,
                            logicalHeight,
                            currentGeometry.centerX,
                            currentGeometry.centerY,
                            currentGeometry.width,
                            currentGeometry.height,
                            currentGeometry.cornerRadius),
                    prismalParams,
                    highlightProfile,
                    PrismalInteractionState.IDLE);
            presentFull(prismalRenderer.outputTexture(), currentOutput);
            if (!presentationSignaled) {
                presentationSignaled = true;
                mainHandler.post(() -> {
                    if (!shuttingDown && listener != null) listener.onPresented();
                });
            }
        } catch (Throwable error) {
            notifyFailure(error);
        }
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
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

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
        if (compositeProgram == 0) {
            compositeProgram = GlProgramUtils.createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
    }

    private void presentFull(int sceneTexture, OutputState current) {
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
        GLES20.glUniform4f(GlProgramUtils.requireUniform(compositeProgram, "uCropRect"), 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlQuadBindings.unbind(compositeProgram);
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

    private void notifyFailure(Throwable error) {
        MainHook.log(TAG + " failure: " + error);
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFailure(error);
        });
    }

                    }
