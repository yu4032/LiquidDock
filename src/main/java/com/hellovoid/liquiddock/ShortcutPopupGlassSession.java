package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
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
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** One-shot workspace backdrop capture plus stable full-screen rendering for ShortcutMenu glass. */
final class ShortcutPopupGlassSession implements RootPassBlurBackend.Consumer {
    interface Listener {
        default void onBackdropReady() {}
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
    private final boolean folderBackdrop;
    // Native Workspace is hidden for modal input safety. Present its real
    // PRE-OPEN frozen desktop blur under the Prismal lens; not wallpaper-only.
    private volatile float backdropOpacity;
    private final LauncherGlassOutputRenderState folderRenderWork = new LauncherGlassOutputRenderState();
    private final FolderGlassFramePacing folderFramePacing = new FolderGlassFramePacing();
    private final FloatBuffer quadBuffer;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile highlightProfile;
    private volatile float workspaceBlurPx;
    private volatile boolean folderClosing;
    // RootPassBlurBackend retains its normalized FBO after freezing popup updates.
    // Reuse that valid texture to re-blur the existing snapshot without recapturing
    // the now-visible menu or changing the PassBlur/EGL owner.
    private int frozenNormalizedTexture;
    private int frozenPhysicalWidth;
    private int frozenPhysicalHeight;
    private volatile float appliedBlur;

    private volatile LauncherGlassGeometry.Snapshot geometry;
    private volatile boolean shuttingDown;
    private volatile boolean backdropPrepared;
    private volatile boolean sourceFrozen;
    private final FolderDesktopCaptureGate desktopCapture = new FolderDesktopCaptureGate();
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean presentationSignaled;

    private PrismalRenderer prismalRenderer;
    private int compositeProgram;
    private int compositePositionLocation = -1;
    private int compositeUvLocation = -1;
    private int compositeTextureLocation = -1;
    private int compositeCropRectLocation = -1;
    private OutputState output;

    ShortcutPopupGlassSession(
            View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener) {
        this(sourceRoot, glassConfig, listener, PassBlurDomain.SHORTCUT_POPUP);
    }

    ShortcutPopupGlassSession(
            View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener,
            PassBlurDomain domain) {
        this(sourceRoot, glassConfig, listener, domain, null);
    }

    ShortcutPopupGlassSession(View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener,
                             PassBlurBindRequest folderCapture) {
        this(sourceRoot, glassConfig, listener, PassBlurDomain.OPEN_FOLDER, folderCapture);
    }

    private ShortcutPopupGlassSession(
            View sourceRoot, LiquidDockConfig.Glass glassConfig, Listener listener,
            PassBlurDomain domain, PassBlurBindRequest folderCapture) {
        if (sourceRoot == null) throw new IllegalArgumentException("sourceRoot == null");
        folderBackdrop = domain == PassBlurDomain.OPEN_FOLDER;
        if (folderBackdrop && folderCapture == null) throw new IllegalArgumentException("Missing wallpaper capture request");
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
        workspaceBlurPx = Miuix307PrismalAdapter.toPortable(optical).blurRadiusPx;
        prismalParams = materialParams(optical, density);
        appliedBlur = prismalParams.blurRadiusPx;
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
                folderBackdrop ? folderCapture
                        : PassBlurBindRequest.shortcutPopup(sourceRoot),
                scalePercent,
                renderFps,
                this,
                "LiquidDock-ShortcutPopup-EGL");
    }

    void requestInitialCapture() {
        if (!shuttingDown) sourceBackend.requestFresh(GENERATION);
    }

    /** Refresh a live popup's optics without touching its frozen producer or EGL surface. */
    void applyLiveGlassConfig(LiquidDockConfig.Glass config) {
        if (shuttingDown || config == null) return;
        View root = sourceRootRef.get();
        float density = root != null ? root.getResources().getDisplayMetrics().density : 1f;
        Miuix307PrismalMaterial.Params optical =
                Miuix307PrismalMaterial.fromConfig(config, density);
        workspaceBlurPx = Miuix307PrismalAdapter.toPortable(optical).blurRadiusPx;
        PrismalParams next = materialParams(optical, density);
        if (folderClosing) next = OpenFolderGlassOptics.restoreWorkspaceBlur(next, workspaceBlurPx);
        final PrismalParams updated = next;
        boolean blurChanged = Float.compare(appliedBlur, updated.blurRadiusPx) != 0;
        appliedBlur = updated.blurRadiusPx;
        prismalParams = updated;
        highlightProfile = config.largeSurfaceHighlightProfile;
        sourceBackend.postToRenderThread(() -> {
            if (shuttingDown) return;
            if (blurChanged && backdropPrepared && frozenNormalizedTexture > 0
                    && frozenPhysicalWidth > 0 && frozenPhysicalHeight > 0) {
                try {
                    sourceBackend.makePbufferCurrent();
                    if (folderBackdrop) prismalRenderer.reblurPreparedBackdrop(updated);
                    else prismalRenderer.prepareBackdrop(
                            frozenNormalizedTexture, frozenPhysicalWidth, frozenPhysicalHeight,
                            logicalWidth, logicalHeight, updated);
                } catch (Throwable error) {
                    MainHook.log(TAG + " live blur refresh failed: " + error);
                    return;
                }
            }
            renderCurrent();
        });
        // Capture-scale/FPS changes affect the next popup capture. An already-frozen popup
        // must not re-capture from behind its own now-visible overlay.
    }

    /** Switch to Workspace blur at the native close boundary. One re-blur of
     * the frozen desktop, no recapture and no per-frame Gaussian passes.
     * Folder refraction/IOR/tint retain their own settings until geometry closes.
     */
    void beginFolderClose() {
        if (!folderBackdrop || shuttingDown || folderClosing) return;
        folderClosing = true;
        final PrismalParams next =
                OpenFolderGlassOptics.restoreWorkspaceBlur(prismalParams, workspaceBlurPx);
        // Serialize the new kernel with all EGL draws. UI-frame geometry updates
        // can continue; the GL thread owns both blur preparation and presentation.
        sourceBackend.postToRenderThread(() -> {
            if (shuttingDown) return;
            // This is a one-shot desktop texture; if Folder and Workspace
            // requested the same blur radius, do not run another two-pass
            // Gaussian at the native closing boundary.
            if (backdropPrepared && prismalRenderer != null
                    && Float.compare(appliedBlur, next.blurRadiusPx) != 0) {
                try {
                    sourceBackend.makePbufferCurrent();
                    prismalRenderer.reblurPreparedBackdrop(next);
                } catch (Throwable error) {
                    MainHook.log(TAG + " folder Workspace-blur handoff failed: " + error);
                    return;
                }
            }
            prismalParams = next;
            appliedBlur = next.blurRadiusPx;
            renderCurrent();
        });
    }

    private PrismalParams materialParams(Miuix307PrismalMaterial.Params optical, float density) {
        PrismalParams base = Miuix307PrismalAdapter.toPortable(optical);
        if (!folderBackdrop) return base;
        SharedPreferences preferences = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        if (preferences == null) return base;
        return OpenFolderGlassOptics.apply(base,
                preferences.getInt(ConfigSchema.Glass.OPEN_FOLDER_BLUR.name(),
                        ConfigSchema.Glass.OPEN_FOLDER_BLUR.runtimeFallback()),
                preferences.getInt(ConfigSchema.Glass.OPEN_FOLDER_IOR.name(),
                        ConfigSchema.Glass.OPEN_FOLDER_IOR.runtimeFallback()),
                preferences.getInt(ConfigSchema.Glass.OPEN_FOLDER_THICKNESS.name(),
                        ConfigSchema.Glass.OPEN_FOLDER_THICKNESS.runtimeFallback()),
                preferences.getInt(ConfigSchema.Glass.OPEN_FOLDER_TINT_ALPHA.name(),
                        ConfigSchema.Glass.OPEN_FOLDER_TINT_ALPHA.runtimeFallback()),
                density);
    }

    boolean hasFrozenBackdrop() {
        return !shuttingDown && backdropPrepared && sourceFrozen;
    }

    /** Atomically stop accepting frames before the native folder opens. */
    boolean sealFolderCapture() {
        if (!folderBackdrop || shuttingDown) return false;
        boolean ready = desktopCapture.seal();
        sourceBackend.setUpdatesEnabled(false, "folder-pre-native-open-fence");
        return ready && hasFrozenBackdrop();
    }

    void updateGeometry(LauncherGlassGeometry.Snapshot next) {
        if (shuttingDown || next == null) return;
        LauncherGlassGeometry.Snapshot old = geometry;
        if (old != null && old.sameAs(next)) return;
        geometry = next;
        sourceBackend.postToRenderThread(this::renderCurrent);
    }

    void updateFolderFrame(LauncherGlassGeometry.Snapshot next, float nativeOpenProgress) {
        if (shuttingDown || !folderBackdrop || next == null) return;
        float opacity = OpenFolderBackdropPolicy.backdropOpacity(nativeOpenProgress);
        LauncherGlassGeometry.Snapshot old = geometry;
        if (old != null && old.sameAs(next) && Float.compare(opacity, backdropOpacity) == 0) return;
        geometry = next;
        backdropOpacity = opacity;
        // The blur snapshot is static. During OEM folder spring, only its
        // presentation opacity and the glass bounds advance.
        if (folderFramePacing.allow(System.nanoTime(), opacity)
                && folderRenderWork.request(true, false)
                && !sourceBackend.postToRenderThread(this::drainFolderFrame)) folderRenderWork.onPostRejected();
    }

    private void drainFolderFrame() {
        if (folderRenderWork.consumeQueuedRender() != 0 && !shuttingDown) renderCurrent();
        if (folderRenderWork.finishQueuedRender()
                && !sourceBackend.postToRenderThread(this::drainFolderFrame)) folderRenderWork.onPostRejected();
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
        // The separate PassBlur authority includes Launcher; freeze only its PRE-OPEN desktop frame.
        if (folderBackdrop && !desktopCapture.mayPrepare()) return;
        try {
            ensureGl();
            logicalWidth = frame.logicalWidth;
            logicalHeight = frame.logicalHeight;
            sourceBackend.makePbufferCurrent();
            frozenNormalizedTexture = frame.normalizedTextureId;
            frozenPhysicalWidth = frame.physicalWidth;
            frozenPhysicalHeight = frame.physicalHeight;
            prismalRenderer.prepareBackdrop(
                    frame.normalizedTextureId,
                    frame.physicalWidth,
                    frame.physicalHeight,
                    frame.logicalWidth,
                    frame.logicalHeight,
                    prismalParams);
            boolean first;
            if (folderBackdrop) {
                // GPU work may complete after the UI thread opened Launcher.
                // Publication is serialized with that native-open fence.
                first = desktopCapture.commit(() -> {
                    backdropPrepared = true;
                    sourceFrozen = true;
                });
                if (!first) return;
            } else {
                backdropPrepared = true;
                first = !sourceFrozen;
                sourceFrozen = true;
            }
            if (first) {
                sourceBackend.setUpdatesEnabled(false, "shortcut-popup-frozen");
                MainHook.log(TAG + " workspace backdrop frozen generation=" + frame.generation);
                mainHandler.post(() -> {
                    if (!shuttingDown && listener != null) listener.onBackdropReady();
                });
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
        if (folderBackdrop && (currentOutput.width != logicalWidth || currentOutput.height != logicalHeight)) {
            notifyFailure(new IllegalStateException("Folder backdrop/output viewport mismatch"));
            return;
        }
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
        compositePositionLocation = compositeUvLocation = -1;
        compositeTextureLocation = compositeCropRectLocation = -1;
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

    private void presentFull(int sceneTexture, OutputState current) {
        sourceBackend.makeCurrent(current.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        // Clear the whole EGL buffer first. The TextureView buffer can be taller than the
        // Launcher logical root; stretching the root-wide Prismal texture to that buffer was the
        // source of the vertical drift found by the stage-3 bisection. Draw the logical scene
        // 1:1 and anchor it to the output's top-left instead.
        GLES20.glViewport(0, 0, current.width, current.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        int viewportWidth = Math.max(1, logicalWidth);
        int viewportHeight = Math.max(1, logicalHeight);
        int viewportBottom = current.height - viewportHeight;
        GLES20.glViewport(0, viewportBottom, viewportWidth, viewportHeight);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositePositionLocation, compositeUvLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if (folderBackdrop) {
            // The frozen source now CONTAINS the real Launcher workspace
            // (icons/widgets/wallpaper). Present its prepared blur below
            // folder optics; native Workspace is hidden to preserve OEM input
            // modality, not because this picture is wallpaper-only.
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendColor(0f, 0f, 0f, backdropOpacity);
            GLES20.glBlendFunc(GLES20.GL_CONSTANT_ALPHA, GLES20.GL_ONE_MINUS_CONSTANT_ALPHA);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prismalRenderer.preparedBlurTexture());
            float firstRow = OpenFolderBackdropPolicy.backdropSampleY(0f);
            float lastRow = OpenFolderBackdropPolicy.backdropSampleY(1f);
            GLES20.glUniform4f(compositeCropRectLocation,
                    0f, firstRow, 1f, lastRow - firstRow);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform4f(compositeCropRectLocation, 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (folderBackdrop) GLES20.glDisable(GLES20.GL_BLEND);
        unbindQuad(compositePositionLocation, compositeUvLocation);
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
