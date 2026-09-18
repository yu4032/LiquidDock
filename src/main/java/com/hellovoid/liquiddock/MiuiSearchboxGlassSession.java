package com.hellovoid.liquiddock;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
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
    private final LockScreenClockGlyphMaskSource glyphMaskSource;
    private final Object glyphMaskLock = new Object();

    private volatile MiuiSearchboxGlassGeometry geometry;
    private LockScreenClockGlyphMaskSource.Mask pendingGlyphMask;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean swapSucceeded;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean presentationSignaled;
    private boolean failureSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private int glyphCompositeProgram;
    private int glyphMaskTexture;
    private long uploadedGlyphMaskSignature = Long.MIN_VALUE;
    private final float[] glyphRootPxToMaskUv = new float[]{
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };
    private float glyphMaskLeft;
    private float glyphMaskTop;
    private float glyphMaskWidth;
    private float glyphMaskHeight;
    private int glyphMaskPixelWidth;
    private int glyphMaskPixelHeight;
    private float glyphSdfRangePx = 32f;
    private OutputState output;

    MiuiSearchboxGlassSession(
            View root,
            LiquidDockConfig.Glass glassConfig,
            ThirdPartyGlassAppearance appearance,
            float cornerRadius,
            Listener listener) {
        this(root, glassConfig, appearance, cornerRadius, listener, PassBlurDomain.MIUI_SEARCHBOX);
    }

    MiuiSearchboxGlassSession(
            View root,
            LiquidDockConfig.Glass glassConfig,
            ThirdPartyGlassAppearance appearance,
            float cornerRadius,
            Listener listener,
            PassBlurDomain domain) {
        this(root, glassConfig, appearance, cornerRadius, listener, domain, null);
    }

    MiuiSearchboxGlassSession(
            View root,
            LiquidDockConfig.Glass glassConfig,
            ThirdPartyGlassAppearance appearance,
            float cornerRadius,
            Listener listener,
            PassBlurDomain domain,
            LockScreenClockGlyphMaskSource explicitGlyphMaskSource) {
        if (root == null) throw new IllegalArgumentException("root == null");
        View sourceRoot = root.getRootView();
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        rootRef = new WeakReference<>(root);
        this.listener = listener;
        this.cornerRadius = Math.max(0f, cornerRadius);
        this.glyphMaskSource = domain == PassBlurDomain.LOCKSCREEN_CLOCK
                ? (explicitGlyphMaskSource != null
                        ? explicitGlyphMaskSource
                        : LockScreenClockGlyphMaskSource.resolve(root))
                : null;
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
        PassBlurBindRequest bindRequest = domain == PassBlurDomain.LOCKSCREEN_CLOCK
                ? PassBlurBindRequest.lockScreenClock(sourceRoot)
                : PassBlurBindRequest.miuiSearchbox(sourceRoot);
        String threadName = domain == PassBlurDomain.LOCKSCREEN_CLOCK
                ? "LiquidDock-LockScreenClock-EGL"
                : "LiquidDock-MiuiSearchbox-EGL";
        sourceBackend = new RootPassBlurBackend(
                sourceRoot,
                bindRequest,
                scalePercent,
                renderFps,
                this,
                threadName);
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

    boolean hasRenderableGlyphGeometry() {
        if (glyphMaskSource == null) return true;
        if (geometry == null) return false;
        synchronized (glyphMaskLock) {
            return pendingGlyphMask != null || glyphMaskTexture != 0;
        }
    }

    void updateGeometry() {
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        View sourceRoot = root.getRootView();
        if (sourceRoot == null || !sourceRoot.isAttachedToWindow()) return;

        MiuiSearchboxGlassGeometry next;
        if (glyphMaskSource != null) {
            LockScreenClockGlyphMaskSource.Mask mask = glyphMaskSource.capture();
            if (mask == null) return;
            float[] bounds = transformedUnitBounds(mask.maskToRoot);
            next = MiuiSearchboxGlassGeometry.fromWindowBounds(
                    mask.rootWidth, mask.rootHeight,
                    bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1], 0f);
            if (next == null) {
                mask.bitmap.recycle();
                return;
            }
            synchronized (glyphMaskLock) {
                LockScreenClockGlyphMaskSource.Mask previous = pendingGlyphMask;
                pendingGlyphMask = mask;
                if (previous != null && previous != mask && !previous.bitmap.isRecycled()) {
                    previous.bitmap.recycle();
                }
            }
        } else {
            next = MiuiSearchboxGlassGeometry.capture(sourceRoot, root, cornerRadius);
            if (next == null) return;
        }

        MiuiSearchboxGlassGeometry old = geometry;
        geometry = next;
        if (old != null && old.sameAs(next) && glyphMaskSource == null) return;
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
            if (glyphMaskSource != null) {
                Api101Bridge.log("[DC][LockScreenClockGlass] fresh PassBlur frame "
                        + frame.logicalWidth + "x" + frame.logicalHeight);
            }
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
            if (glyphCompositeProgram != 0) GLES20.glDeleteProgram(glyphCompositeProgram);
            if (glyphMaskTexture != 0) GLES20.glDeleteTextures(1, new int[]{glyphMaskTexture}, 0);
            compositeProgram = 0;
            glyphCompositeProgram = 0;
            glyphMaskTexture = 0;
            synchronized (glyphMaskLock) {
                if (pendingGlyphMask != null && !pendingGlyphMask.bitmap.isRecycled()) {
                    pendingGlyphMask.bitmap.recycle();
                }
                pendingGlyphMask = null;
            }
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
            if (glyphMaskSource != null) {
                uploadPendingGlyphMask();
                if (glyphMaskTexture == 0) return;
                prismalRenderer.drawMaskGlass(
                        currentGeometry.toPrismalGeometry(),
                        prismalParams,
                        highlightProfile,
                        glyphMaskTexture,
                        glyphRootPxToMaskUv,
                        glyphSdfRangePx);
                presentFull(prismalRenderer.outputTexture(), current);
            } else {
                prismalRenderer.drawGlass(
                        currentGeometry.toPrismalGeometry(),
                        prismalParams,
                        highlightProfile,
                        PrismalInteractionState.IDLE);
                presentFull(prismalRenderer.outputTexture(), current);
            }
            swapSucceeded = true;
        } catch (Throwable error) {
            notifyFailure("render", error);
        }
    }

    private void ensureGl() {
        sourceBackend.makePbufferCurrent();
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        // Lockscreen glyphs render inside PrismalRenderer through drawMaskGlass(); no secondary
        // material/composite program is allowed here.
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
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"), 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        sourceBackend.swapBuffers(current.eglSurface);
    }


    private void uploadPendingGlyphMask() {
        LockScreenClockGlyphMaskSource.Mask mask;
        synchronized (glyphMaskLock) {
            mask = pendingGlyphMask;
            pendingGlyphMask = null;
        }
        if (mask == null) return;
        try {
            android.graphics.Matrix maskToRoot =
                    new android.graphics.Matrix();
            maskToRoot.setValues(mask.maskToRoot);
            android.graphics.Matrix rootToMask =
                    new android.graphics.Matrix();
            if (!maskToRoot.invert(rootToMask)) {
                throw new IllegalStateException("glyph transform not invertible");
            }
            float[] androidMatrix = new float[9];
            rootToMask.getValues(androidMatrix);
            // android.graphics.Matrix is row-major; GLES mat3 upload is column-major.
            glyphRootPxToMaskUv[0] = androidMatrix[0];
            glyphRootPxToMaskUv[1] = androidMatrix[3];
            glyphRootPxToMaskUv[2] = androidMatrix[6];
            glyphRootPxToMaskUv[3] = androidMatrix[1];
            glyphRootPxToMaskUv[4] = androidMatrix[4];
            glyphRootPxToMaskUv[5] = androidMatrix[7];
            glyphRootPxToMaskUv[6] = androidMatrix[2];
            glyphRootPxToMaskUv[7] = androidMatrix[5];
            glyphRootPxToMaskUv[8] = androidMatrix[8];

            float[] bounds = transformedUnitBounds(mask.maskToRoot);
            glyphMaskLeft = bounds[0] / Math.max(1f, mask.rootWidth);
            glyphMaskTop = bounds[1] / Math.max(1f, mask.rootHeight);
            glyphMaskWidth = (bounds[2] - bounds[0]) / Math.max(1f, mask.rootWidth);
            glyphMaskHeight = (bounds[3] - bounds[1]) / Math.max(1f, mask.rootHeight);
            glyphMaskPixelWidth = Math.max(1, mask.bitmap.getWidth());
            glyphMaskPixelHeight = Math.max(1, mask.bitmap.getHeight());
            glyphSdfRangePx = Math.max(1f, mask.sdfRangePx);

            if (mask.signature == uploadedGlyphMaskSignature && glyphMaskTexture != 0) return;
            if (glyphMaskTexture == 0) {
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                glyphMaskTexture = textures[0];
                if (glyphMaskTexture == 0) throw new IllegalStateException("glyph mask texture=0");
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphMaskTexture);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphMaskTexture);
            }
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mask.bitmap, 0);
            Api101Bridge.log("[DC][LockScreenClockGlass] glyph SDF content uploaded "
                    + mask.bitmap.getWidth() + "x" + mask.bitmap.getHeight());
            uploadedGlyphMaskSignature = mask.signature;
        } finally {
            if (!mask.bitmap.isRecycled()) mask.bitmap.recycle();
        }
    }

    private static float[] transformedUnitBounds(float[] m) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float[][] points = new float[][]{
                {0f, 0f}, {1f, 0f}, {0f, 1f}, {1f, 1f}
        };
        for (float[] p : points) {
            float x = m[0] * p[0] + m[1] * p[1] + m[2];
            float y = m[3] * p[0] + m[4] * p[1] + m[5];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private void presentGlyphMasked(
            int backdropTexture,
            int blurredBackdropTexture,
            OutputState current) {
        sourceBackend.makeCurrent(current.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, current.width, current.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(glyphCompositeProgram);
        bindQuad(glyphCompositeProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTexture);
        GLES20.glUniform1i(requireUniform(glyphCompositeProgram, "uBackdrop"), 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurredBackdropTexture);
        GLES20.glUniform1i(requireUniform(glyphCompositeProgram, "uBlurredBackdrop"), 1);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphMaskTexture);
        GLES20.glUniform1i(requireUniform(glyphCompositeProgram, "uGlyphMask"), 2);

        GLES20.glUniform4f(requireUniform(glyphCompositeProgram, "uGlyphRect"),
                glyphMaskLeft, glyphMaskTop, glyphMaskWidth, glyphMaskHeight);
        GLES20.glUniformMatrix3fv(
                requireUniform(glyphCompositeProgram, "uRootPxToMaskUv"),
                1, false, glyphRootPxToMaskUv, 0);
        GLES20.glUniform2f(requireUniform(glyphCompositeProgram, "uRootSize"),
                Math.max(1, logicalWidth), Math.max(1, logicalHeight));
        GLES20.glUniform2f(requireUniform(glyphCompositeProgram, "uGlyphTexel"),
                1f / Math.max(1, glyphMaskPixelWidth),
                1f / Math.max(1, glyphMaskPixelHeight));
        GLES20.glUniform2f(requireUniform(glyphCompositeProgram, "uOutputTexel"),
                1f / Math.max(1, current.width),
                1f / Math.max(1, current.height));
        GLES20.glUniform2f(requireUniform(glyphCompositeProgram, "uGlyphLightDir"),
                prismalParams.lightDirX, prismalParams.lightDirY);
        GLES20.glUniform4f(requireUniform(glyphCompositeProgram, "uGlyphTint"),
                prismalParams.tintR, prismalParams.tintG,
                prismalParams.tintB, prismalParams.tintA);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uGlyphBrightness"),
                prismalParams.brightness);

        float glyphRefractionPx = Math.max(1f,
                prismalParams.glassThicknessPx * prismalParams.displacementScale * 0.35f);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uGlyphRefractionPx"),
                glyphRefractionPx);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uGlyphNormalStrength"),
                Math.max(0.25f, prismalParams.normalStrength));
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uGlyphHighlightWidth"),
                Math.max(0.08f, Math.min(0.8f, prismalParams.highlightWidth / 12f)));
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uGlyphHighlightStrength"),
                Math.max(0f, prismalParams.specular + prismalParams.rimStrength) * 0.35f);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uRimStrength"),
                prismalParams.rimStrength);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uPlainHighlight"),
                prismalParams.plainHighlight);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uComponentLitRim"),
                highlightProfile.litRim ? 1f : 0f);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uComponentOppositeRim"),
                highlightProfile.oppositeRim ? 1f : 0f);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uComponentFaceSheen"),
                highlightProfile.faceSheen ? 1f : 0f);
        GLES20.glUniform1f(requireUniform(glyphCompositeProgram, "uComponentPlainHighlight"),
                highlightProfile.plainHighlight ? 1f : 0f);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(glyphCompositeProgram);
        sourceBackend.swapBuffers(current.eglSurface);
        Api101Bridge.log("[DC][LockScreenClockGlass] glyph-backdrop swap success");
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
            String summary = failureSummary(error);
            Api101Bridge.log(TAG + " session failure stage=" + stage + " cause=" + summary);
            if (glyphMaskSource != null) {
                Api101Bridge.log("[DC][LockScreenClockGlass] session failure stage="
                        + stage + " cause=" + summary, error);
            }
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
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
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
