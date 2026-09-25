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

/** Frozen full-screen backdrop plus live Prismal geometry for SearchActivityBackground. */
final class MiuiSearchboxGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        void onPresented();
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][MiuiSearchboxGlass]";
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
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
    }

    private final WeakReference<View> rootRef;
    private final Handler mainHandler;
    private final Listener listener;
    private final RootPassBlurBackend sourceBackend;
    private final MiuiSearchboxSnapshotState snapshotState = new MiuiSearchboxSnapshotState();
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;
    private final float cornerRadius;

    private volatile MiuiSearchboxGlassGeometry geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean swapSucceeded;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean presentationSignaled;
    private boolean failureSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private OutputState output;

    MiuiSearchboxGlassSession(
            View root,
            LiquidDockConfig.Glass glassConfig,
            ThirdPartyGlassAppearance appearance,
            float cornerRadius,
            Listener listener) {
        if (root == null) throw new IllegalArgumentException("root == null");
        View sourceRoot = root.getRootView();
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        rootRef = new WeakReference<>(root);
        this.listener = listener;
        this.cornerRadius = Math.max(0f, cornerRadius);
        mainHandler = new Handler(root.getContext().getMainLooper());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        float density = root.getResources().getDisplayMetrics().density;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        PrismalParams baseParams = Miuix307PrismalAdapter.toPortable(optical);
        prismalParams = ThirdPartyPrismalParams.apply(baseParams, appearance);
        highlightProfile = appearance != null
                ? appearance.highlightProfile
                : (glassConfig != null
                    ? glassConfig.largeSurfaceHighlightProfile
                    : PrismalHighlightProfile.ALL_ENABLED);
        int scalePercent = appearance != null
                ? appearance.captureScalePercent
                : (glassConfig != null
                    ? glassConfig.passBlurCaptureScalePercent
                    : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT);
        int renderFps = appearance != null
                ? appearance.renderFps
                : (glassConfig != null
                    ? glassConfig.passBlurRenderFps
                    : PassBlurQualityPolicy.DEFAULT_RENDER_FPS);
        sourceBackend = new RootPassBlurBackend(
                sourceRoot,
                PassBlurBindRequest.miuiSearchbox(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-MiuiSearchbox-EGL");
    }

    void requestFreshCapture() {
        if (shuttingDown) return;
        backdropPrepared = false;
        swapSucceeded = false;
        presentationSignaled = false;
        snapshotState.beginCapture();
        sourceBackend.reconcileRoot();
        sourceBackend.requestFresh(GENERATION);
    }

    void reconcileRoot() {
        if (!shuttingDown) sourceBackend.reconcileRoot();
    }

    void updateGeometry() {
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        View sourceRoot = root.getRootView();
        if (sourceRoot == null || !sourceRoot.isAttachedToWindow()) return;
        MiuiSearchboxGlassGeometry next = MiuiSearchboxGlassGeometry.capture(
                sourceRoot, root, cornerRadius);
        if (next == null) return;
        MiuiSearchboxGlassGeometry old = geometry;
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
                notifyFailure("output-attach", error);
            }
        })) surface.release();
    }

    void resizeOutput(int width, int height) {
        if (shuttingDown) return;
        updateGeometry();
        sourceBackend.reconcileRoot();
        sourceBackend.postToRenderThread(() -> {
            OutputState current = output;
            if (current == null) return;
            current.width = Math.max(1, width);
            current.height = Math.max(1, height);
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

    void onOutputPresented() {
        if (shuttingDown || presentationSignaled || !swapSucceeded) return;
        presentationSignaled = true;
        if (listener != null) listener.onPresented();
    }

    @Override
    public void onFreshFrame(RootPassBlurBackend backend, RootPassBlurFrame frame) {
        if (shuttingDown || backend != sourceBackend || frame == null
                || frame.generation != GENERATION || !snapshotState.acceptFreshFrame()) return;
        try {
            ensureGl();
            logicalWidth = frame.logicalWidth;
            logicalHeight = frame.logicalHeight;
            updateGeometry();
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
            sourceBackend.setUpdatesEnabled(false, "searchbox-snapshot-latched");
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

    private void renderCurrent() {
        OutputState current = output;
        View root = rootRef.get();
        MiuiSearchboxGlassGeometry currentGeometry = geometry;
        if (shuttingDown || !backdropPrepared || current == null
                || currentGeometry == null
                || current.eglSurface == EGL14.EGL_NO_SURFACE
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
            presentFull(prismalRenderer.outputTexture(), current);
            swapSucceeded = true;
        } catch (Throwable error) {
            notifyFailure("render", error);
        }
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

    private void notifyFailure(String stage, Throwable error) {
        if (shuttingDown || failureSignaled) return;
        failureSignaled = true;
        try {
            Api101Bridge.log(TAG + " session failure stage=" + stage + " cause=" + failureSummary(error));
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

                    }
