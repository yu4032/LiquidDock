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
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** One frozen PassBlur source for shortcut popup or expanded-folder Prismal lens output. */
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
    // Keep the wallpaper-only GPU snapshot as an optical input; the visible
    // OPEN_FOLDER output must never composite a second fullscreen background.
    private volatile float backdropOpacity;
    private final LauncherGlassOutputRenderState folderRenderWork = new LauncherGlassOutputRenderState();
    private final FloatBuffer quadBuffer;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile highlightProfile;
    private volatile boolean folderOutputFrozen;
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
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean presentationSignaled;
    private boolean captureRetirementRequested;
    private final float sourceDensity;

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
        sourceDensity = density;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
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

    /** After the first EGL presentation, retire only the OPEN_FOLDER capture
     * producer; maintain normalized GPU input and Prismal background textures
     * until the cached lens finishes fading out.
     */
    boolean retireFolderCapture(Runnable onRetired) {
        if (!folderBackdrop || shuttingDown || !backdropPrepared || !sourceFrozen
                || !presentationSignaled || captureRetirementRequested) return false;
        boolean queued = sourceBackend.retireFrozenFolderProducer(GENERATION, onRetired);
        if (queued) captureRetirementRequested = true;
        return queued;
    }

    /** Refresh a live popup's optics without touching its frozen producer or EGL surface. */
    void applyLiveGlassConfig(LiquidDockConfig.Glass config) {
        if (shuttingDown || folderOutputFrozen || config == null) return;
        View root = sourceRootRef.get();
        float density = root != null
                ? root.getResources().getDisplayMetrics().density : sourceDensity;
        Miuix307PrismalMaterial.Params optical =
                Miuix307PrismalMaterial.fromConfig(config, density);
        PrismalParams next = materialParams(optical, density);
        final PrismalParams updated = next;
        boolean blurChanged = Float.compare(appliedBlur, updated.blurRadiusPx) != 0;
        appliedBlur = updated.blurRadiusPx;
        prismalParams = updated;
        highlightProfile = config.largeSurfaceHighlightProfile;
        sourceBackend.postToRenderThread(() -> {
            if (shuttingDown || folderOutputFrozen) return;
            if (blurChanged && backdropPrepared && frozenNormalizedTexture > 0
                    && frozenPhysicalWidth > 0 && frozenPhysicalHeight > 0) {
                try {
                    sourceBackend.makePbufferCurrent();
                    if (folderBackdrop) {
                        prismalRenderer.reblurPreparedBackdrop(updated);
                    } else prismalRenderer.prepareBackdrop(
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

    /** Keep the last GPU buffer while the View performs its closing fade. */
    void freezeFolderOutput() {
        if (!folderBackdrop || shuttingDown) return;
        folderOutputFrozen = true;
        sourceBackend.setUpdatesEnabled(false, "folder-cached-close");
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

    /** Re-present the preserved GPU snapshot after a temporary Recents cover.
     * Does not rebind or recreate the retired OPEN_FOLDER PassBlur producer.
     */
    void requestCachedGlassRedraw() {
        if (!shuttingDown && backdropPrepared) {
            sourceBackend.postToRenderThread(this::renderCurrent);
        }
    }

    void updateGeometry(LauncherGlassGeometry.Snapshot next) {
        if (shuttingDown || next == null) return;
        LauncherGlassGeometry.Snapshot old = geometry;
        if (old != null && old.sameAs(next)) return;
        geometry = next;
        sourceBackend.postToRenderThread(this::renderCurrent);
    }

    void updateFolderFrame(LauncherGlassGeometry.Snapshot next, float nativeOpenProgress) {
        if (shuttingDown || folderOutputFrozen || !folderBackdrop || next == null) return;
        float opacity = OpenFolderBackdropPolicy.backdropOpacity(nativeOpenProgress);
        LauncherGlassGeometry.Snapshot old = geometry;
        if (old != null && old.sameAs(next) && Float.compare(opacity, backdropOpacity) == 0) return;
        geometry = next;
        backdropOpacity = opacity;
        // Both springs follow native geometry. A separate time limiter drops
        // intermediate opening positions at high refresh rates. The render
        // queue coalesces only while busy and always consumes the latest frame.
        if (folderRenderWork.request(true, false)
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
        if (shuttingDown || folderOutputFrozen || backend != sourceBackend || frame == null
                || frame.generation != GENERATION) return;
        // The Launcher and Dock surfaces are excluded for OPEN_FOLDER, so a
        // late wallpaper frame cannot recursively contain FolderCling.
        if (folderBackdrop && sourceFrozen) return;
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
            backdropPrepared = true;
            if (!sourceFrozen) {
                sourceFrozen = true;
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
        if (shuttingDown || folderOutputFrozen || !backdropPrepared || currentGeometry == null
                || currentOutput == null || currentOutput.eglSurface == EGL14.EGL_NO_SURFACE
                || logicalWidth <= 0 || logicalHeight <= 0) return;
        if (folderBackdrop && (currentOutput.width != logicalWidth || currentOutput.height != logicalHeight)) {
            notifyFailure(new IllegalStateException("Folder backdrop/output viewport mismatch"));
            return;
        }
        try {
            ensureGl();
            sourceBackend.makePbufferCurrent();
            PrismalParams drawParams = prismalParams;
            // The folder output is already a logical 1:1 full-root surface.
            // Render into its back buffer directly, avoiding an extra full-screen
            // RGBA target and copy while the native folder spring is running.
            if (folderBackdrop) {
                sourceBackend.makeCurrent(currentOutput.eglSurface);
                prismalRenderer.beginGlassFrameOnSurface(currentOutput.width, currentOutput.height);
            } else {
                prismalRenderer.beginGlassFrame();
            }
            prismalRenderer.setEdgeDecoration(
                    GlassEdgeEffects.current(folderBackdrop
                            ? GlassEdgeEffects.FOLDER : GlassEdgeEffects.POPUP),
                    sourceDensity);
            prismalRenderer.drawGlass(
                    new PrismalGeometry(
                            logicalWidth,
                            logicalHeight,
                            currentGeometry.centerX,
                            currentGeometry.centerY,
                            currentGeometry.width,
                            currentGeometry.height,
                            currentGeometry.cornerRadius),
                    drawParams,
                    highlightProfile,
                    1f);
            if (folderBackdrop) {
                sourceBackend.swapBuffers(currentOutput.eglSurface);
            } else {
                presentFull(prismalRenderer.outputTexture(), currentOutput);
            }
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
        shutdown(null);
    }

    /** Source capture window remains attached until compositor detach + EGL release. */
    void shutdown(Runnable onShutdownComplete) {
        sourceBackend.onShutdownComplete(onShutdownComplete);
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
        if (prismalRenderer == null) {
            prismalRenderer = new PrismalRenderer();
        }
        if (!folderBackdrop && compositeProgram == 0) {
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

    /** Only the Prismal scene is visible. The normalized frozen wallpaper and
     * both prepared Gaussian textures remain GPU sampling inputs; they must
     * never be painted as a second full-screen wallpaper by OPEN_FOLDER.
     * Launcher/Recents owns the wallpaper blur, zoom, and dimming throughout
     * the complete interactive gesture, so there is no mid-gesture mode swap.
     */
    private void presentFull(int sceneTexture, OutputState current) {
        sourceBackend.makeCurrent(current.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, current.width, current.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // Preserve the audited logical 1:1 viewport and top-left anchoring
        // when the TextureView Surface is larger than the Launcher root.
        int viewportWidth = Math.max(1, logicalWidth);
        int viewportHeight = Math.max(1, logicalHeight);
        int viewportBottom = current.height - viewportHeight;
        GLES20.glViewport(0, viewportBottom, viewportWidth, viewportHeight);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositePositionLocation, compositeUvLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform4f(compositeCropRectLocation, 0f, 0f, 1f, 1f);
        // Preserve the RGBA values of the transparent Prismal FBO.
        // Alpha blending against the cleared output with GL_ONE incorrectly
        // treats straight-alpha glass pixels as premultiplied.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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
