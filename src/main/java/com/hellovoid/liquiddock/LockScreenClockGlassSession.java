package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.util.SparseArray;
import android.view.Surface;
import android.view.View;

import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Lockscreen-only Prismal session.
 *
 * <p>The PassBlur backdrop is captured once after native glyph suppression. During native clock
 * animation only glyph-local-to-root matrices change; SDF textures are uploaded solely when text
 * or local font/layout content changes.</p>
 */
final class LockScreenClockGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        void onPresented();
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][LockScreenClockGlass]";
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

    private static final class GlyphState {
        int texture;
        long signature = Long.MIN_VALUE;
        final float[] rootPxToMaskUv = new float[9];
        float sdfRangeRootPx = 1f;
        MiuiSearchboxGlassGeometry geometry;
    }

    private final WeakReference<View> anchorRef;
    private final Handler mainHandler;
    private final Listener listener;
    private final LockScreenClockGlyphMaskSource glyphSource;
    private final RootPassBlurBackend sourceBackend;
    private final MiuiSearchboxSnapshotState snapshotState = new MiuiSearchboxSnapshotState();
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;
    private final Object frameLock = new Object();
    private final SparseArray<GlyphState> glyphStates = new SparseArray<>();

    private LockScreenClockGlyphMaskSource.Frame pendingFrame;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean swapSucceeded;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private volatile int renderableGlyphCount;
    private boolean presentationSignaled;
    private boolean failureSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private OutputState output;

    LockScreenClockGlassSession(
            View anchor,
            LockScreenClockGlyphMaskSource glyphSource,
            LiquidDockConfig.Glass glassConfig,
            ThirdPartyGlassAppearance appearance,
            Listener listener) {
        if (anchor == null) throw new IllegalArgumentException("anchor == null");
        if (glyphSource == null || !glyphSource.hasGlyphs()) {
            throw new IllegalArgumentException("glyphSource unavailable");
        }
        View sourceRoot = anchor.getRootView();
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        anchorRef = new WeakReference<>(anchor);
        this.glyphSource = glyphSource;
        this.listener = listener;
        mainHandler = new Handler(anchor.getContext().getMainLooper());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        float density = anchor.getResources().getDisplayMetrics().density;
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
                PassBlurBindRequest.lockScreenClock(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-LockScreenClock-EGL");
    }

    void updateGeometry() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            throw new IllegalStateException("clock geometry must be captured on main thread");
        }
        if (shuttingDown) return;
        LockScreenClockGlyphMaskSource.Frame next = glyphSource.captureFrame();
        if (next == null || next.isEmpty()) return;
        synchronized (frameLock) {
            recycleFrame(pendingFrame);
            pendingFrame = next;
        }
        sourceBackend.postToRenderThread(this::renderCurrent);
    }

    boolean hasRenderableGlyphGeometry() {
        synchronized (frameLock) {
            return pendingFrame != null || renderableGlyphCount > 0;
        }
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
            sourceBackend.setUpdatesEnabled(false, "lockscreen-clock-backdrop-latched");
            Api101Bridge.log(TAG + " fresh PassBlur frame "
                    + frame.logicalWidth + "x" + frame.logicalHeight);
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
        synchronized (frameLock) {
            recycleFrame(pendingFrame);
            pendingFrame = null;
        }
        boolean queued = sourceBackend.postToRenderThread(() -> {
            releaseOutput(output);
            output = null;
            for (int i = 0; i < glyphStates.size(); i++) {
                GlyphState state = glyphStates.valueAt(i);
                if (state.texture != 0) {
                    GLES20.glDeleteTextures(1, new int[]{state.texture}, 0);
                }
            }
            glyphStates.clear();
            renderableGlyphCount = 0;
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
        if (shuttingDown) return;
        try {
            ensureGl();
            applyPendingFrame();
            OutputState current = output;
            if (!backdropPrepared || current == null
                    || current.eglSurface == EGL14.EGL_NO_SURFACE
                    || logicalWidth <= 0 || logicalHeight <= 0
                    || renderableGlyphCount <= 0) return;

            sourceBackend.makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            int drawn = 0;
            for (int i = 0; i < glyphStates.size(); i++) {
                GlyphState state = glyphStates.valueAt(i);
                if (state.texture == 0 || state.geometry == null) continue;
                if (state.geometry.rootWidth != logicalWidth
                        || state.geometry.rootHeight != logicalHeight) continue;
                prismalRenderer.drawMaskGlass(
                        state.geometry.toPrismalGeometry(),
                        prismalParams,
                        highlightProfile,
                        state.texture,
                        state.rootPxToMaskUv,
                        state.sdfRangeRootPx);
                drawn++;
            }
            if (drawn == 0) return;
            presentFull(prismalRenderer.outputTexture(), current);
            swapSucceeded = true;
        } catch (Throwable error) {
            notifyFailure("render", error);
        }
    }

    private void applyPendingFrame() {
        LockScreenClockGlyphMaskSource.Frame frame;
        synchronized (frameLock) {
            frame = pendingFrame;
            pendingFrame = null;
        }
        if (frame == null) return;
        Set<Integer> active = new HashSet<>();
        try {
            for (LockScreenClockGlyphMaskSource.GlyphFrame glyph : frame.glyphs) {
                active.add(glyph.slot);
                GlyphState state = glyphStates.get(glyph.slot);
                if (state == null) {
                    state = new GlyphState();
                    glyphStates.put(glyph.slot, state);
                }

                if (glyph.bitmap != null && state.signature != glyph.contentSignature) {
                    uploadGlyphTexture(state, glyph.bitmap);
                    state.signature = glyph.contentSignature;
                    Api101Bridge.log(TAG + " glyph SDF uploaded slot=" + glyph.slot
                            + " size=" + glyph.bitmap.getWidth() + "x" + glyph.bitmap.getHeight());
                }
                if (state.texture == 0) continue;

                Matrix maskToRoot = new Matrix();
                maskToRoot.setValues(glyph.maskToRoot);
                Matrix rootToMask = new Matrix();
                if (!maskToRoot.invert(rootToMask)) continue;
                float[] androidMatrix = new float[9];
                rootToMask.getValues(androidMatrix);
                // android.graphics.Matrix is row-major; GLES mat3 upload is column-major.
                state.rootPxToMaskUv[0] = androidMatrix[0];
                state.rootPxToMaskUv[1] = androidMatrix[3];
                state.rootPxToMaskUv[2] = androidMatrix[6];
                state.rootPxToMaskUv[3] = androidMatrix[1];
                state.rootPxToMaskUv[4] = androidMatrix[4];
                state.rootPxToMaskUv[5] = androidMatrix[7];
                state.rootPxToMaskUv[6] = androidMatrix[2];
                state.rootPxToMaskUv[7] = androidMatrix[5];
                state.rootPxToMaskUv[8] = androidMatrix[8];
                state.sdfRangeRootPx = Math.max(1f, glyph.sdfRangeRootPx);
                state.geometry = MiuiSearchboxGlassGeometry.fromWindowBounds(
                        glyph.rootWidth,
                        glyph.rootHeight,
                        glyph.rootBounds.left,
                        glyph.rootBounds.top,
                        glyph.rootBounds.width(),
                        glyph.rootBounds.height(),
                        0f);
            }

            for (int i = glyphStates.size() - 1; i >= 0; i--) {
                int slot = glyphStates.keyAt(i);
                if (active.contains(slot)) continue;
                GlyphState stale = glyphStates.valueAt(i);
                if (stale.texture != 0) GLES20.glDeleteTextures(1, new int[]{stale.texture}, 0);
                glyphStates.removeAt(i);
            }

            int count = 0;
            for (int i = 0; i < glyphStates.size(); i++) {
                GlyphState state = glyphStates.valueAt(i);
                if (state.texture != 0 && state.geometry != null) count++;
            }
            renderableGlyphCount = count;
        } finally {
            recycleFrame(frame);
        }
    }

    private static void uploadGlyphTexture(GlyphState state, Bitmap bitmap) {
        if (state.texture == 0) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            state.texture = textures[0];
            if (state.texture == 0) throw new IllegalStateException("glyph texture=0");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, state.texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, state.texture);
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
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
                    + " cause=" + failureSummary(error), error);
        } catch (Throwable ignored) {}
        mainHandler.post(() -> {
            if (!shuttingDown && listener != null) listener.onFailure(error);
        });
    }

    private static void recycleFrame(LockScreenClockGlyphMaskSource.Frame frame) {
        if (frame == null) return;
        for (LockScreenClockGlyphMaskSource.GlyphFrame glyph : frame.glyphs) {
            Bitmap bitmap = glyph.bitmap;
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
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
