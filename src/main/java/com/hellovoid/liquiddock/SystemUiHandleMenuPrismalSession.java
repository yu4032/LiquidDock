package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Dedicated zero-copy PassBlur -> Prismal pipeline for HyperOS' app-caption Handle Menu.
 *
 * <p>The source authority is the caption window's real ViewRoot surface — the same window-level
 * compositor boundary on which native pass-window blur already samples the app behind the menu.
 * This class deliberately avoids the generic RootPassBlurBackend lifecycle and binds only this
 * short-lived 758x147 menu session.</p>
 */
final class SystemUiHandleMenuPrismalSession {
    interface Listener {
        void onFirstFramePresented();
        void onFailure(Throwable error);
    }

    private static final String TAG = "[DC][SystemUiHandleMenuPrismal]";
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };
    private final View host;
    private final View sourceRoot;
    private final SurfaceControl sourceSurface;
    private final RootPassBlurEndpointBridge.Endpoint sourceEndpoint;
    private final RootPassBlurContentRect sourceContentRect;
    private final Listener listener;
    private final Handler mainHandler;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final FloatBuffer quadBuffer;
    private final PrismalParams prismalParams;
    private final PrismalHighlightProfile highlightProfile;
    private final float[] textureMatrix = new float[16];

    private volatile boolean shuttingDown;
    private volatile boolean failureReported;
    private volatile boolean sourceBound;
    private volatile boolean firstFramePresented;
    private volatile boolean sourceFrameReady;
    private volatile int width;
    private volatile int height;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface pbufferSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface outputEglSurface = EGL14.EGL_NO_SURFACE;

    private SurfaceTexture inputTexture;
    private Surface inputProducerSurface;
    private Surface outputSurface;
    private int oesTexture;
    private int normalizeProgram;
    private int normalizedTexture;
    private int normalizedFramebuffer;
    private int normalizedWidth;
    private int normalizedHeight;
    private PrismalRenderer prismalRenderer;
    private int compositeProgram;

    private Miuix307PassBlurBridge.Binding sourceBinding;

    SystemUiHandleMenuPrismalSession(
            View host,
            View sourceRoot,
            LiquidDockConfig.Glass glassConfig,
            Listener listener) {
        if (host == null) throw new IllegalArgumentException("host == null");
        if (sourceRoot == null || !sourceRoot.isAttachedToWindow()) {
            throw new IllegalArgumentException("sourceRoot unavailable");
        }
        RootPassBlurEndpointBridge.Endpoint endpoint =
                RootPassBlurEndpointBridge.inspect(sourceRoot);
        if (endpoint == null || !endpoint.isValid()) {
            throw new IllegalArgumentException("source ViewRoot endpoint unavailable");
        }

        this.host = host;
        this.sourceRoot = sourceRoot;
        sourceEndpoint = endpoint;
        sourceSurface = endpoint.rootSurface;
        sourceContentRect = RootPassBlurContentRect.resolve(
                endpoint.surfaceWidth,
                endpoint.surfaceHeight,
                endpoint.insetLeft,
                endpoint.insetTop,
                endpoint.insetRight,
                endpoint.insetBottom);
        this.listener = listener;
        mainHandler = new Handler(host.getContext().getMainLooper());

        float density = host.getResources().getDisplayMetrics().density;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        prismalParams = Miuix307PrismalAdapter.toPortable(optical);
        highlightProfile = glassConfig != null
                ? glassConfig.largeSurfaceHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;

        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        renderThread = new HandlerThread("LiquidDock-SystemUiHandleMenu-Prismal");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    void start(int width, int height) {
        if (shuttingDown) return;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        renderHandler.post(() -> {
            try {
                ensureEgl();
                ensureSource();
                mainHandler.post(this::bindSource);
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    void attachOutput(Surface surface, int width, int height) {
        if (surface == null || shuttingDown) {
            if (surface != null) surface.release();
            return;
        }
        renderHandler.post(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureEgl();
                releaseOutput();
                outputSurface = surface;
                this.width = Math.max(1, width);
                this.height = Math.max(1, height);
                outputEglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
                checkEgl("eglCreateWindowSurface",
                        outputEglSurface != EGL14.EGL_NO_SURFACE);
                ensureRenderResources();
                renderLatestIfPossible();
            } catch (Throwable error) {
                try { surface.release(); } catch (Throwable ignored) {}
                fail(error);
            }
        });
    }

    void resizeOutput(int width, int height) {
        if (shuttingDown) return;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        renderHandler.post(() -> {
            if (shuttingDown) return;
            // attachInsideTarget() can publish the first visual size before start() has initialized
            // EGL. Treat that callback as geometry only; start()/attachOutput() will consume the
            // stored dimensions once the EGL domain exists.
            if (!isEglReady()) return;
            try {
                makePbufferCurrent();
                ensureNormalizedTarget(this.width, this.height);
                renderLatestIfPossible();
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    void detachOutput(Surface surface) {
        if (surface == null) return;
        renderHandler.post(() -> {
            if (outputSurface == surface) {
                releaseOutput();
            } else {
                try { surface.release(); } catch (Throwable ignored) {}
            }
        });
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        try {
            mainHandler.post(this::unbindSource);
        } catch (Throwable error) {
            log("unbind enqueue failed: " + error);
        }
        try {
            renderHandler.post(() -> {
                try { releaseOutput(); } catch (Throwable ignored) {}
                try { releaseRenderResources(); } catch (Throwable ignored) {}
                try { releaseSource(); } catch (Throwable ignored) {}
                try { releaseEgl(); } catch (Throwable ignored) {}
            });
        } catch (Throwable error) {
            log("render cleanup enqueue failed: " + error);
        }
        try {
            renderThread.quitSafely();
        } catch (Throwable error) {
            log("render thread shutdown failed: " + error);
        }
    }

    private void bindSource() {
        if (shuttingDown || sourceBound || inputProducerSurface == null
                || !sourceSurface.isValid() || !sourceRoot.isAttachedToWindow()) return;
        try {
            Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(
                    PassBlurBindRequest.systemUiHandleMenu(sourceRoot),
                    inputProducerSurface);
            if (next == null) {
                fail(new IllegalStateException("ViewRoot PassBlur producer bind unavailable"));
                return;
            }
            if (!RootPassBlurEndpointBridge.sameGeneration(next, sourceEndpoint)) {
                Miuix307PassBlurBridge.unbind(next);
                fail(new IllegalStateException("ViewRoot generation changed during bind"));
                return;
            }
            sourceBinding = next;
            sourceBound = true;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void unbindSource() {
        if (!sourceBound && sourceBinding == null) return;
        sourceBound = false;
        Miuix307PassBlurBridge.Binding current = sourceBinding;
        sourceBinding = null;
        try {
            Miuix307PassBlurBridge.unbind(current);
        } catch (Throwable error) {
            log("ViewRoot producer unbind failed: " + error);
        }
    }

    private void ensureSource() {
        makePbufferCurrent();
        if (inputTexture != null && inputProducerSurface != null && oesTexture != 0) return;
        oesTexture = createOesTexture();
        inputTexture = new SurfaceTexture(oesTexture);
        inputTexture.setDefaultBufferSize(
                Math.max(1, sourceEndpoint.bufferWidth),
                Math.max(1, sourceEndpoint.bufferHeight));
        inputProducerSurface = new Surface(inputTexture);
        inputTexture.setOnFrameAvailableListener(this::onFrameAvailable, renderHandler);
    }

    private void onFrameAvailable(SurfaceTexture texture) {
        if (shuttingDown || texture == null || texture != inputTexture) return;
        try {
            makePbufferCurrent();
            texture.updateTexImage();
            texture.getTransformMatrix(textureMatrix);
            ensureRenderResources();
            normalizeBackdrop();
            sourceFrameReady = true;
            renderGlass();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void renderLatestIfPossible() {
        if (!sourceFrameReady || normalizedTexture == 0
                || outputEglSurface == EGL14.EGL_NO_SURFACE) return;
        renderGlass();
    }

    private void normalizeBackdrop() {
        ensureNormalizedTarget(Math.max(1, width), Math.max(1, height));
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, normalizedFramebuffer);
        GLES20.glViewport(0, 0, normalizedWidth, normalizedHeight);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(
                requireUniform(normalizeProgram, "uTexMatrix"),
                1, false, textureMatrix, 0);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"),
                sourceContentRect.left,
                sourceContentRect.bottom,
                sourceContentRect.width,
                sourceContentRect.height);
        GLES20.glUniform1i(
                requireUniform(normalizeProgram, "uConfigRot"),
                sourceEndpoint.rotation);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uValidDockRect"),
                0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void renderGlass() {
        if (!sourceFrameReady || normalizedTexture == 0
                || outputEglSurface == EGL14.EGL_NO_SURFACE
                || width <= 0 || height <= 0) return;
        makePbufferCurrent();
        prismalRenderer.prepareBackdrop(
                normalizedTexture,
                normalizedWidth,
                normalizedHeight,
                width,
                height,
                prismalParams);
        prismalRenderer.beginGlassFrame();
        float radius = Math.max(1f, Math.min(width, height) * 0.5f);
        prismalRenderer.drawGlass(
                new PrismalGeometry(
                        width, height,
                        width * 0.5f, height * 0.5f,
                        width, height, radius),
                prismalParams,
                highlightProfile,
                PrismalInteractionState.IDLE);

        makeOutputCurrent();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prismalRenderer.outputTexture());
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        checkEgl("eglSwapBuffers", EGL14.eglSwapBuffers(eglDisplay, outputEglSurface));

        if (!firstFramePresented) {
            firstFramePresented = true;
            try {
                mainHandler.post(() -> {
                    if (shuttingDown || listener == null) return;
                    try {
                        listener.onFirstFramePresented();
                    } catch (Throwable error) {
                        log("first-frame listener failed: " + error);
                    }
                });
            } catch (Throwable error) {
                log("first-frame callback enqueue failed: " + error);
            }
        }
    }

    private void ensureRenderResources() {
        makePbufferCurrent();
        if (normalizeProgram == 0) {
            normalizeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        }
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
    }

    private void ensureNormalizedTarget(int width, int height) {
        if (normalizedFramebuffer != 0
                && normalizedWidth == width && normalizedHeight == height) return;
        releaseNormalizedTarget();
        normalizedTexture = createTexture2d(width, height);
        normalizedFramebuffer = createFramebuffer(normalizedTexture);
        normalizedWidth = width;
        normalizedHeight = height;
    }

    private boolean isEglReady() {
        return eglDisplay != EGL14.EGL_NO_DISPLAY
                && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null
                && pbufferSurface != EGL14.EGL_NO_SURFACE;
    }

    private void ensureEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY
                && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null
                && pbufferSurface != EGL14.EGL_NO_SURFACE) return;

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEgl("eglGetDisplay", eglDisplay != EGL14.EGL_NO_DISPLAY);
        int[] version = new int[2];
        checkEgl("eglInitialize",
                EGL14.eglInitialize(eglDisplay, version, 0, version, 1));

        int[] configAttrs = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        checkEgl("eglChooseConfig", EGL14.eglChooseConfig(
                eglDisplay, configAttrs, 0, configs, 0, 1, count, 0)
                && count[0] > 0 && configs[0] != null);
        eglConfig = configs[0];

        eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreateContext", eglContext != EGL14.EGL_NO_CONTEXT);

        pbufferSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay, eglConfig,
                new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreatePbufferSurface",
                pbufferSurface != EGL14.EGL_NO_SURFACE);
        makePbufferCurrent();
    }

    private void makePbufferCurrent() {
        checkEgl("eglMakeCurrent(pbuffer)", EGL14.eglMakeCurrent(
                eglDisplay, pbufferSurface, pbufferSurface, eglContext));
    }

    private void makeOutputCurrent() {
        checkEgl("eglMakeCurrent(output)", outputEglSurface != EGL14.EGL_NO_SURFACE
                && EGL14.eglMakeCurrent(
                        eglDisplay, outputEglSurface, outputEglSurface, eglContext));
    }

    private void releaseOutput() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && outputEglSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, outputEglSurface); }
            catch (Throwable ignored) {}
        }
        outputEglSurface = EGL14.EGL_NO_SURFACE;
        Surface surface = outputSurface;
        outputSurface = null;
        if (surface != null) {
            try { surface.release(); } catch (Throwable ignored) {}
        }
    }

    private void releaseSource() {
        SurfaceTexture texture = inputTexture;
        inputTexture = null;
        if (texture != null) {
            try { texture.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}
        }
        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        if (producer != null) {
            try { producer.release(); } catch (Throwable ignored) {}
        }
        if (texture != null) {
            try { texture.release(); } catch (Throwable ignored) {}
        }
        if (oesTexture != 0) {
            try {
                makePbufferCurrent();
                GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
            } catch (Throwable ignored) {}
            oesTexture = 0;
        }
    }

    private void releaseRenderResources() {
        try { makePbufferCurrent(); } catch (Throwable ignored) {}
        releaseNormalizedTarget();
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (normalizeProgram != 0) GLES20.glDeleteProgram(normalizeProgram);
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
        normalizeProgram = 0;
        compositeProgram = 0;
    }

    private void releaseNormalizedTarget() {
        if (normalizedFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{normalizedFramebuffer}, 0);
        }
        if (normalizedTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{normalizedTexture}, 0);
        }
        normalizedFramebuffer = 0;
        normalizedTexture = 0;
        normalizedWidth = 0;
        normalizedHeight = 0;
    }

    private void releaseEgl() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return;
        try {
            EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
        } catch (Throwable ignored) {}
        if (pbufferSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, pbufferSurface); }
            catch (Throwable ignored) {}
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext); }
            catch (Throwable ignored) {}
        }
        try { EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        pbufferSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    private int createOesTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    private static int createTexture2d(int width, int height) {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return texture[0];
    }

    private static int createFramebuffer(int texture) {
        int[] framebuffer = new int[1];
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texture,
                0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(
                    "framebuffer incomplete 0x" + Integer.toHexString(status));
        }
        return framebuffer[0];
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

    private static void checkEgl(String operation, boolean success) {
        if (!success) {
            throw new IllegalStateException(
                    operation + " failed egl=0x" + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void fail(Throwable error) {
        if (shuttingDown || failureReported) return;
        failureReported = true;
        log("failure: " + error);
        try {
            boolean posted = mainHandler.post(() -> {
                if (shuttingDown || listener == null) return;
                try {
                    listener.onFailure(error);
                } catch (Throwable callbackError) {
                    log("failure listener failed: " + callbackError);
                }
            });
            if (!posted) {
                log("failure callback rejected by main looper");
            }
        } catch (Throwable enqueueError) {
            log("failure callback enqueue failed: " + enqueueError);
        }
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
