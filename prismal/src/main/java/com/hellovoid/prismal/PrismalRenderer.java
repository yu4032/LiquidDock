package com.hellovoid.prismal;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable OpenGL ES 2.0 Prismal renderer.
 *
 * <p>The caller owns EGL/current-context lifetime and supplies a normal GL_TEXTURE_2D framebuffer
 * texture in GL-native bottom-left orientation. This class standardizes that texture into the
 * orientation used by upstream Prismal, executes Prismal's original 0.5x blur passes and vertex
 * shader, then applies LiquidDock's narrow single-edge transmitted-refraction correction to the
 * vendored upstream fragment before compilation. It returns a transparent full-frame texture
 * containing only the
 * rendered glass. It has no dependency on View, SurfaceTexture, OES, Dock, Xposed, HyperOS, Context, or Resources.</p>
 */
public final class PrismalRenderer implements AutoCloseable {
    private static final float BLUR_FBO_SCALE = 0.5f;

    private static final float[] FULL_QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };
    private static final float[] GLASS_QUAD = new float[]{
            -0.5f, -0.5f,
             0.5f, -0.5f,
            -0.5f,  0.5f,
            -0.5f,  0.5f,
             0.5f, -0.5f,
             0.5f,  0.5f
    };
    private static final float[] BLUR_QUAD = new float[]{
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
            -1f,  1f,
             1f, -1f,
             1f,  1f
    };

    // Boundary adapter only. It converts a conventional FBO texture (v=0 visual bottom) into the
    // same row orientation Prismal receives from GLUtils.texImage2D(Bitmap) (v=0 visual top).
    private static final String SOURCE_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aUv;
            }
            """;
    private static final String SOURCE_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            varying vec2 vUv;
            void main() {
                gl_FragColor = texture2D(uTexture, vec2(vUv.x, 1.0 - vUv.y));
            }
            """;

    private final FloatBuffer fullQuad;
    private final FloatBuffer glassQuad;
    private final FloatBuffer blurQuad;
    // Match upstream Prismal's renderer semantics: resolve each glass-program uniform after
    // link, retain its location (including -1 for linker-inactive declarations), and pass
    // that location directly to glUniform*. OpenGL deliberately ignores location -1.
    private final Map<String, Integer> glassUniformLocations = new HashMap<>();

    private int sourceProgram;
    private int blurHProgram;
    private int blurVProgram;
    private int glassProgram;

    // Program locations are immutable after link. Cache them once instead of crossing the
    // Java/driver boundary with glGet*Location on every backdrop rebuild.
    private int sourcePositionLocation = -1;
    private int sourceUvLocation = -1;
    private int sourceTextureLocation = -1;
    private int blurHPositionLocation = -1;
    private int blurHTextureLocation = -1;
    private int blurHTexelSizeLocation = -1;
    private int blurHSigmaLocation = -1;
    private int blurVPositionLocation = -1;
    private int blurVTextureLocation = -1;
    private int blurVTexelSizeLocation = -1;
    private int blurVSigmaLocation = -1;
    private int glassPositionLocation = -1;

    // GL uniform values persist until the program is relinked. PrismalParams is immutable, so the
    // renderer can upload the large frame-invariant optics block only when the params instance
    // actually changes. Geometry/opacity/interaction uniforms remain per-node.
    private PrismalParams cachedStaticParams;
    private int cachedHighlightMask = Integer.MIN_VALUE;
    private int cachedResolutionWidth = -1;
    private int cachedResolutionHeight = -1;
    private int cachedBlurWidth = -1;
    private int cachedBlurHeight = -1;
    private float cachedBlurSigma = Float.NaN;
    private boolean glassTexturesBound;
    private final PrismalFrameTarget frameTarget = new PrismalFrameTarget();

    // Reused by the GL thread; do not allocate one clip rect for every icon per frame.
    private final PrismalNodeScissor.Rect nodeScissor =
            new PrismalNodeScissor.Rect(0, 0, 0, 0);
    private int sourceTexture;
    private int sourceFramebuffer;
    private int blurTextureH;
    private int blurFramebufferH;
    private int blurTextureV;
    private int blurFramebufferV;
    private int outputTexture;
    private int outputFramebuffer;
    private int reducedOutputTexture;
    private int reducedOutputFramebuffer;
    private int reducedOutputWidth;
    private int reducedOutputHeight;
    // width/height remain Prismal's public logical framebuffer domain. The backing
    // FBOs may use fewer physical pixels without changing any geometry or screen UV.
    private int width;
    private int height;
    private int renderWidth;
    private int renderHeight;
    // Source normalization/blur may be downsampled, but procedural glass and its SDF edge are
    // rasterized in logical pixels so edge quality is independent from capture scale.
    private int outputWidth;
    private int outputHeight;
    private int blurWidth;
    private int blurHeight;
    private boolean backdropPrepared;
    // Newly allocated GL textures have undefined contents. A region clear is valid only after
    // the output target has received one complete clear.
    private boolean outputNeedsFullClear = true;
    private boolean glassFrameBegun;
    private int glassDrawCount;
    private boolean legacySingleDraw;

    public PrismalRenderer() {
        fullQuad = floatBuffer(FULL_QUAD);
        glassQuad = floatBuffer(GLASS_QUAD);
        blurQuad = floatBuffer(BLUR_QUAD);
    }

    /**
     * Render one frame. The returned texture is owned by this renderer and remains valid until
     * resize/release. Pixels outside the glass quad remain transparent.
     */
    public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params) {
        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (params == null) params = PrismalParams.builder().build();
        // Keep the existing Dock entry point and optics model. Batch rendering only splits the
        // same source/blur/draw sequence so Launcher can reuse one prepared backdrop.
        ensurePrograms();
        ensureTargets(geometry.framebufferWidth, geometry.framebufferHeight);

        int[] previousFbo = new int[1];
        int[] previousViewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0);
        try {
            prepareBackdrop(backgroundTexture2D, geometry.framebufferWidth,
                    geometry.framebufferHeight, params);
            beginGlassFrame();
            legacySingleDraw = true;
            try {
                drawGlass(geometry, params);
            } finally {
                legacySingleDraw = false;
            }
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("Prismal GLES error=0x" + Integer.toHexString(error));
            }
            return outputTexture;
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0]);
            GLES20.glViewport(previousViewport[0], previousViewport[1],
                    previousViewport[2], previousViewport[3]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }

    /** Prepare one normalized/blurred backdrop for one or more glass nodes. */
    public void prepareBackdrop(int backgroundTexture2D, int framebufferWidth, int framebufferHeight,
                                PrismalParams params) {
        prepareBackdrop(backgroundTexture2D, framebufferWidth, framebufferHeight,
                framebufferWidth, framebufferHeight, params);
    }

    /**
     * Prepare a lower-density physical backdrop while retaining full logical Prismal coordinates.
     * Geometry, u_resolution and pixel-valued optics stay in logical framebuffer pixels. Source
     * normalization and blur may use fewer physical pixels, while the final glass/SDF/highlight
     * raster remains at logical output resolution. Existing callers keep physical == logical.
     */
    public void prepareBackdrop(int backgroundTexture2D,
                                int physicalWidth, int physicalHeight,
                                int logicalFramebufferWidth, int logicalFramebufferHeight,
                                PrismalParams params) {
        if (backgroundTexture2D <= 0) throw new IllegalArgumentException("background texture <= 0");
        if (physicalWidth <= 0 || physicalHeight <= 0
                || logicalFramebufferWidth <= 0 || logicalFramebufferHeight <= 0) {
            throw new IllegalArgumentException("framebuffer dimensions <= 0");
        }
        if (params == null) params = PrismalParams.builder().build();
        ensurePrograms();
        ensureTargets(physicalWidth, physicalHeight,
                logicalFramebufferWidth, logicalFramebufferHeight);
        width = logicalFramebufferWidth;
        height = logicalFramebufferHeight;
        renderSourceAdapter(backgroundTexture2D);
        renderBlur(params);
        backdropPrepared = true;
        glassFrameBegun = false;
        glassDrawCount = 0;
    }

    /** Clear the transparent scene output once before appending glass nodes. */
    public void beginGlassFrame() {
        beginGlassFrameRegion(0f, 0f, width, height);
    }

    /**
     * Draw into framebuffer zero of the caller's current RGBA EGL window surface.
     * The window is fully cleared on every frame because its back buffer may rotate.
     * Logical coordinates and all optics are unchanged. This does not update outputTexture();
     * subsequent texture frames must call beginGlassFrame/Region as usual.
     */
    public void beginGlassFrameOnSurface(int surfaceWidth, int surfaceHeight) {
        if (!backdropPrepared) {
            throw new IllegalStateException("prepareBackdrop must be called before beginGlassFrame");
        }
        frameTarget.selectSurface(surfaceWidth, surfaceHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameTarget.framebuffer);
        GLES20.glViewport(0, 0, frameTarget.width, frameTarget.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // Keep outputNeedsFullClear intact: the internal texture has not been touched.
        glassFrameBegun = true;
        glassDrawCount = 0;
        glassTexturesBound = false;
    }

    /** Temporary lower-density glass optics; logical geometry and backdrop UV remain unchanged. */
    public void beginGlassFrameAtScale(int percent) {
        if (!backdropPrepared) {
            throw new IllegalStateException("prepareBackdrop must be called before beginGlassFrame");
        }
        int nextWidth = PrismalFrameTarget.scaledDimension(width, percent);
        int nextHeight = PrismalFrameTarget.scaledDimension(height, percent);
        if (nextWidth == outputWidth && nextHeight == outputHeight) {
            // A live quality change back to native density must not retain the obsolete
            // reduced output FBO alongside the already-allocated full-size output.
            if (reducedOutputFramebuffer != 0) releaseReducedOutput();
            beginGlassFrame();
            return;
        }
        if (reducedOutputTexture == 0 || reducedOutputWidth != nextWidth
                || reducedOutputHeight != nextHeight) {
            releaseReducedOutput();
            reducedOutputWidth = nextWidth;
            reducedOutputHeight = nextHeight;
            reducedOutputTexture = createTexture(nextWidth, nextHeight);
            reducedOutputFramebuffer = createFramebuffer(reducedOutputTexture);
        }
        frameTarget.selectTexture(reducedOutputFramebuffer, nextWidth, nextHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameTarget.framebuffer);
        GLES20.glViewport(0, 0, frameTarget.width, frameTarget.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        glassFrameBegun = true;
        glassDrawCount = 0;
        glassTexturesBound = false;
    }

    /**
     * Clear only one dirty logical region of the transparent scene output.
     *
     * <p>The output FBO remains full-root so screen-space Prismal coordinates and backdrop
     * sampling are unchanged. Callers that only move one glass object can clear the union of the
     * previous and current bounds instead of invalidating the whole root-sized texture.</p>
     */
    public void beginGlassFrameRegion(
            float logicalLeft,
            float logicalTop,
            float logicalRight,
            float logicalBottom) {
        if (!backdropPrepared) {
            throw new IllegalStateException("prepareBackdrop must be called before beginGlassFrame");
        }
        float left;
        float top;
        float right;
        float bottom;
        if (outputNeedsFullClear) {
            left = 0f;
            top = 0f;
            right = width;
            bottom = height;
        } else {
            left = Math.max(0f, Math.min(width, logicalLeft));
            top = Math.max(0f, Math.min(height, logicalTop));
            right = Math.max(left, Math.min(width, logicalRight));
            bottom = Math.max(top, Math.min(height, logicalBottom));
        }

        frameTarget.selectTexture(outputFramebuffer, outputWidth, outputHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        float scaleX = outputWidth / (float) Math.max(1, width);
        float scaleY = outputHeight / (float) Math.max(1, height);
        int scissorLeft = Math.max(0, (int) Math.floor(left * scaleX));
        int scissorRight = Math.min(outputWidth, (int) Math.ceil(right * scaleX));
        int scissorBottom = Math.max(
                0, (int) Math.floor((height - bottom) * scaleY));
        int scissorTop = Math.min(
                outputHeight, (int) Math.ceil((height - top) * scaleY));
        int scissorWidth = Math.max(1, scissorRight - scissorLeft);
        int scissorHeight = Math.max(1, scissorTop - scissorBottom);
        GLES20.glScissor(scissorLeft, scissorBottom, scissorWidth, scissorHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        outputNeedsFullClear = false;
        glassFrameBegun = true;
        glassDrawCount = 0;
        glassTexturesBound = false;
    }

    /** Append one glass node using the currently prepared backdrop. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params) {
        drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED);
    }

    /** Append one glass node with a renderer-scoped highlight selection. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile) {
        drawGlass(geometry, params, highlightProfile, null);
    }

    /** Append one glass node with an optional per-node touch interaction override. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params,
                          PrismalHighlightProfile highlightProfile,
                          PrismalInteractionState interactionState) {
        drawGlass(geometry, params, highlightProfile, interactionState, 1f);
    }

    /** Append one glass node with interaction and an output-alpha multiplier. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params,
                          PrismalHighlightProfile highlightProfile,
                          PrismalInteractionState interactionState, float opacity) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        if (highlightProfile == null) highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        float safeOpacity = Float.isFinite(opacity)
                ? Math.max(0f, Math.min(1f, opacity)) : 1f;
        renderGlassNode(geometry, params, highlightProfile, interactionState,
                !legacySingleDraw || glassDrawCount > 0, safeOpacity);
        glassDrawCount++;
    }

    /** Append one glass node with an output-alpha multiplier. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params, float opacity) {
        drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED, opacity);
    }

    /** Append one glass node with per-draw highlights and an output-alpha multiplier. */
    public void drawGlass(PrismalGeometry geometry, PrismalParams params,
                          PrismalHighlightProfile highlightProfile, float opacity) {
        if (geometry == null) throw new IllegalArgumentException("geometry == null");
        if (!glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != width || geometry.framebufferHeight != height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) params = PrismalParams.builder().build();
        if (highlightProfile == null) highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        float safeOpacity = Float.isFinite(opacity)
                ? Math.max(0f, Math.min(1f, opacity)) : 1f;
        renderGlassNode(geometry, params, highlightProfile, null,
                !legacySingleDraw || glassDrawCount > 0, safeOpacity);
        glassDrawCount++;
    }

    public int outputTexture() {
        return reducedOutputFramebuffer != 0 && frameTarget.framebuffer == reducedOutputFramebuffer
                ? reducedOutputTexture : outputTexture;
    }
    public int framebufferWidth() { return width; }
    public int framebufferHeight() { return height; }

    private void ensurePrograms() {
        if (sourceProgram != 0 && blurHProgram != 0 && blurVProgram != 0 && glassProgram != 0) {
            return;
        }
        sourceProgram = createProgram(SOURCE_VERTEX, SOURCE_FRAGMENT);
        blurHProgram = createProgram(PrismalShaderSources.BLUR_VERTEX, PrismalShaderSources.BLUR_H);
        blurVProgram = createProgram(PrismalShaderSources.BLUR_VERTEX, PrismalShaderSources.BLUR_V);
        String glassFragment = PrismalComponentGateShader.apply(
                PrismalOpticalEdgeShader.apply(
                        PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
        String glassVertex = PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX);
        glassProgram = createProgram(glassVertex, glassFragment);
        glassUniformLocations.clear();
        if (sourceProgram == 0 || blurHProgram == 0 || blurVProgram == 0 || glassProgram == 0) {
            throw new IllegalStateException("Prismal shader program creation failed");
        }
        sourcePositionLocation = requireAttrib(sourceProgram, "aPosition");
        sourceUvLocation = requireAttrib(sourceProgram, "aUv");
        sourceTextureLocation = requireUniform(sourceProgram, "uTexture");
        blurHPositionLocation = requireAttrib(blurHProgram, "a_position");
        blurHTextureLocation = requireUniform(blurHProgram, "u_texture");
        blurHTexelSizeLocation = requireUniform(blurHProgram, "u_texelSize");
        blurHSigmaLocation = requireUniform(blurHProgram, "u_sigma");
        blurVPositionLocation = requireAttrib(blurVProgram, "a_position");
        blurVTextureLocation = requireUniform(blurVProgram, "u_texture");
        blurVTexelSizeLocation = requireUniform(blurVProgram, "u_texelSize");
        blurVSigmaLocation = requireUniform(blurVProgram, "u_sigma");
        glassPositionLocation = requireAttrib(glassProgram, "a_position");

        // Sampler units are immutable for the lifetime of these linked programs.
        GLES20.glUseProgram(sourceProgram);
        GLES20.glUniform1i(sourceTextureLocation, 0);
        GLES20.glUseProgram(blurHProgram);
        GLES20.glUniform1i(blurHTextureLocation, 0);
        GLES20.glUseProgram(blurVProgram);
        GLES20.glUniform1i(blurVTextureLocation, 0);

        // Glass samplers and the blur-source selector are also program constants.
        GLES20.glUseProgram(glassProgram);
        GLES20.glUniform1i(glassUniformLocation("u_backgroundTexture"), 0);
        GLES20.glUniform1i(glassUniformLocation("u_blurredTexture"), 1);
        GLES20.glUniform1i(glassUniformLocation("u_useBlurredTexture"), 1);
        cachedStaticParams = null;
        cachedHighlightMask = Integer.MIN_VALUE;
        cachedResolutionWidth = -1;
        cachedResolutionHeight = -1;
        cachedBlurWidth = -1;
        cachedBlurHeight = -1;
        cachedBlurSigma = Float.NaN;
        glassTexturesBound = false;
    }

    private void ensureTargets(int nextWidth, int nextHeight) {
        ensureTargets(nextWidth, nextHeight, nextWidth, nextHeight);
    }

    private void ensureTargets(int nextRenderWidth, int nextRenderHeight,
                               int nextOutputWidth, int nextOutputHeight) {
        if (renderWidth == nextRenderWidth && renderHeight == nextRenderHeight
                && outputWidth == nextOutputWidth && outputHeight == nextOutputHeight
                && outputTexture != 0) {
            return;
        }
        releaseTargets();
        renderWidth = Math.max(1, nextRenderWidth);
        renderHeight = Math.max(1, nextRenderHeight);
        outputWidth = Math.max(1, nextOutputWidth);
        outputHeight = Math.max(1, nextOutputHeight);
        blurWidth = Math.max(1, (int) (renderWidth * BLUR_FBO_SCALE));
        blurHeight = Math.max(1, (int) (renderHeight * BLUR_FBO_SCALE));

        sourceTexture = createTexture(renderWidth, renderHeight);
        sourceFramebuffer = createFramebuffer(sourceTexture);
        blurTextureH = createTexture(blurWidth, blurHeight);
        blurFramebufferH = createFramebuffer(blurTextureH);
        blurTextureV = createTexture(blurWidth, blurHeight);
        blurFramebufferV = createFramebuffer(blurTextureV);
        outputTexture = createTexture(outputWidth, outputHeight);
        outputFramebuffer = createFramebuffer(outputTexture);
        outputNeedsFullClear = true;
    }

    private void renderSourceAdapter(int inputTexture) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sourceFramebuffer);
        GLES20.glViewport(0, 0, renderWidth, renderHeight);
        // Full-screen opaque overwrite: clearing first only adds tile/driver work.
        GLES20.glUseProgram(sourceProgram);
        bindInterleavedQuad(sourcePositionLocation, sourceUvLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindInterleavedQuad(sourcePositionLocation, sourceUvLocation);
    }

    private void renderBlur(PrismalParams p) {
        float scaleX = renderWidth / (float) Math.max(1, width);
        float scaleY = renderHeight / (float) Math.max(1, height);
        float physicalScale = Math.max(0.0001f, Math.min(scaleX, scaleY));
        float sigma = Math.max(p.blurRadiusPx * physicalScale * BLUR_FBO_SCALE, 0.5f);
        boolean updateTexelSize = cachedBlurWidth != blurWidth || cachedBlurHeight != blurHeight;
        boolean updateSigma = Float.compare(cachedBlurSigma, sigma) != 0;
        renderBlurPass(blurHProgram, sourceTexture, blurFramebufferH, sigma,
                blurHPositionLocation, blurHTexelSizeLocation, blurHSigmaLocation,
                updateTexelSize, updateSigma);
        renderBlurPass(blurVProgram, blurTextureH, blurFramebufferV, sigma,
                blurVPositionLocation, blurVTexelSizeLocation, blurVSigmaLocation,
                updateTexelSize, updateSigma);
        cachedBlurWidth = blurWidth;
        cachedBlurHeight = blurHeight;
        cachedBlurSigma = sigma;
    }

    private void renderBlurPass(int program, int inputTexture, int framebuffer, float sigma,
                                int position, int texelSizeLocation, int sigmaLocation,
                                boolean updateTexelSize, boolean updateSigma) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, blurWidth, blurHeight);
        // Blur shader writes every pixel in the viewport; a preceding clear is redundant.
        GLES20.glUseProgram(program);
        blurQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, blurQuad);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        if (updateTexelSize) {
            GLES20.glUniform2f(texelSizeLocation,
                    1f / Math.max(1, blurWidth), 1f / Math.max(1, blurHeight));
        }
        if (updateSigma) GLES20.glUniform1f(sigmaLocation, sigma);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
    }

    private void renderGlassNode(PrismalGeometry g, PrismalParams p,
                                 PrismalHighlightProfile highlights,
                                 PrismalInteractionState interactionState,
                                 boolean composite, float opacity) {
        highlights = highlights.withOs4EdgeReplacingLegacyEdge();
        // The fragment shader renders a full-frame quad per glass node. Restrict its expensive
        // refraction/highlight shading to the SDF silhouette with an AA guard. This preserves
        // native-pixel edge fidelity at reduced backdrop capture density.
        if (!PrismalNodeScissor.computeInto(
                g, frameTarget.width, frameTarget.height, nodeScissor)) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameTarget.framebuffer);
        GLES20.glViewport(0, 0, frameTarget.width, frameTarget.height);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(nodeScissor.x, nodeScissor.y,
                nodeScissor.width, nodeScissor.height);
        if (composite) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFuncSeparate(
                    GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                    GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GLES20.glDisable(GLES20.GL_BLEND);
        }
        GLES20.glUseProgram(glassProgram);

        glassQuad.position(0);
        GLES20.glEnableVertexAttribArray(glassPositionLocation);
        GLES20.glVertexAttribPointer(
                glassPositionLocation, 2, GLES20.GL_FLOAT, false, 0, glassQuad);

        if (cachedResolutionWidth != width || cachedResolutionHeight != height) {
            uniform2f("u_resolution", width, height);
            cachedResolutionWidth = width;
            cachedResolutionHeight = height;
        }

        // Per-node geometry stays dynamic.
        uniform2f("u_mousePos", g.centerX, height - g.centerY);
        uniform2f("u_glassSize", g.glassWidth, g.glassHeight);
        uniform4f("u_cornerRadii", g.topLeftRadius, g.topRightRadius,
                g.bottomRightRadius, g.bottomLeftRadius);

        if (cachedStaticParams != p) {
            uploadStaticGlassParams(p);
            cachedStaticParams = p;
        }

        // Lens reach is the only optics value that depends on this node's dimensions.
        float minGlassDim = Math.min(g.glassWidth, g.glassHeight);
        float domeBoost = 1f + 0.55f * clamp(p.liquidDome, 0f, 2f);
        float refractionHeight = p.heightTransitionWidthPx * domeBoost;
        float lensPx = refractionHeight * 2f * p.displacementScale * p.lensRefractionScale;
        uniform1f("u_lensRefractionPx", clamp(lensPx, 0f, Math.max(4f, minGlassDim * 0.85f)));

        // Opacity and interaction can vary per node even when PrismalParams is shared.
        uniform1f("u_transmittance", p.transmittance * opacity);
        float pressProgress = interactionState != null ? interactionState.pressProgress : p.pressProgress;
        float glowCenterX = interactionState != null ? interactionState.glowCenterX : p.glowCenterX;
        float glowCenterY = interactionState != null ? interactionState.glowCenterY : p.glowCenterY;
        uniform1f("u_pressProgress", pressProgress);
        uniform2f("u_glowCenter", glowCenterX, glowCenterY);

        int highlightMask = highlightMask(highlights);
        if (cachedHighlightMask != highlightMask) {
            uploadHighlightMask(highlightMask);
            cachedHighlightMask = highlightMask;
        }

        // prepareBackdrop's blur pass leaves unrelated texture bindings behind. Bind the glass
        // source pair once for the first node of each frame; later nodes share the same backdrop.
        if (!glassTexturesBound) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureV);
            glassTexturesBound = true;
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(glassPositionLocation);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void uploadStaticGlassParams(PrismalParams p) {
        uniform1f("u_refractionInset", p.refractionInsetPx);
        uniform1f("u_sminSmoothing", p.sminSmoothingPx);
        uniform1f("u_edgeRefractionFalloff", p.edgeRefractionFalloff);
        uniform1f("u_ior", p.ior);
        uniform1f("u_glassThickness", p.glassThicknessPx);
        uniform1f("u_normalStrength", p.normalStrength);
        uniform1f("u_displacementScale", p.displacementScale);
        uniform1f("u_heightTransitionWidth", p.heightTransitionWidthPx);
        uniform1f("u_lensDepthEffect", p.lensDepthEffect);
        uniform1f("u_chromaticAberration", Math.max(0f, p.chromaticAberration));
        uniform1f("u_dispersionR", p.dispersionR);
        uniform1f("u_dispersionB", p.dispersionB);
        uniform1f("u_vibrancy", p.vibrancy);
        uniform1f("u_plainHighlight", p.plainHighlight);
        uniform1f("u_liquidDome", p.liquidDome);
        uniform1f("u_fresnelReflect", p.fresnelReflect);
        uniform1f("u_brightness", p.brightness);
        uniform4f("u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);
        uniform1f("u_highlightWidth", p.highlightWidth);
        uniform1f("u_os4EdgeWidthPx", p.os4EdgeWidthPx);
        uniform1f("u_os4ReflectOffsetPx", p.os4ReflectOffsetPx);
        uniform1f("u_os4ReflectionStrength", p.os4ReflectionStrength);
        uniform1f("u_os4ReflectionLighten", p.os4ReflectionLighten);
        uniform1f("u_os4DirectionalAngleRange", p.os4DirectionalAngleRange);
        uniform1f("u_os4DirectionalIntensity", p.os4DirectionalIntensity);
        uniform1f("u_os4DirectionalOppositeIntensity", p.os4DirectionalOppositeIntensity);
        uniform2f("u_lightDir", p.lightDirX, p.lightDirY);
        uniform1f("u_specular", p.specular);
        uniform1f("u_shininess", p.shininess);
        uniform1f("u_rimStrength", p.rimStrength);
        uniform4f("u_shadowColor", p.shadowR, p.shadowG, p.shadowB, p.shadowA);
        uniform1f("u_shadowSoftness", p.shadowSoftness);
        uniform1f("u_causticIntensity", p.causticIntensity);
        uniform2f("u_backdropSampleScale", p.backdropScaleX, p.backdropScaleY);
        uniform1f("u_parallaxScale", p.parallaxScale);
        uniform1f("u_backdropPinch", p.backdropPinch);
        uniform1f("u_glowStrength", p.glowStrength);
        uniform1i("u_showNormals", p.showNormals ? 1 : 0);
    }

    private static int highlightMask(PrismalHighlightProfile h) {
        int mask = 0;
        if (h.skyHaze) mask |= 1;
        if (h.specular) mask |= 1 << 1;
        if (h.litRim) mask |= 1 << 2;
        if (h.oppositeRim) mask |= 1 << 3;
        if (h.cornerRim) mask |= 1 << 4;
        if (h.faceSheen) mask |= 1 << 5;
        if (h.plainHighlight) mask |= 1 << 6;
        if (h.caustics) mask |= 1 << 7;
        if (h.pressGlow) mask |= 1 << 8;
        if (h.os4Edge) mask |= 1 << 9;
        return mask;
    }

    private void uploadHighlightMask(int mask) {
        uniform1f("u_componentSkyHaze", (mask & 1) != 0 ? 1f : 0f);
        uniform1f("u_componentSpecular", (mask & (1 << 1)) != 0 ? 1f : 0f);
        uniform1f("u_componentLitRim", (mask & (1 << 2)) != 0 ? 1f : 0f);
        uniform1f("u_componentOppositeRim", (mask & (1 << 3)) != 0 ? 1f : 0f);
        uniform1f("u_componentCornerRim", (mask & (1 << 4)) != 0 ? 1f : 0f);
        uniform1f("u_componentFaceSheen", (mask & (1 << 5)) != 0 ? 1f : 0f);
        uniform1f("u_componentPlainHighlight", (mask & (1 << 6)) != 0 ? 1f : 0f);
        uniform1f("u_componentCaustics", (mask & (1 << 7)) != 0 ? 1f : 0f);
        uniform1f("u_componentPressGlow", (mask & (1 << 8)) != 0 ? 1f : 0f);
        uniform1f("u_os4EdgeEnabled", (mask & (1 << 9)) != 0 ? 1f : 0f);
    }

    private void bindInterleavedQuad(int position, int uv) {
        fullQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, fullQuad);
        fullQuad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, fullQuad);
    }

    private void unbindInterleavedQuad(int position, int uv) {
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private int createTexture(int w, int h) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        return ids[0];
    }

    private int createFramebuffer(int texture) {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Prismal framebuffer incomplete=0x"
                    + Integer.toHexString(status));
        }
        return ids[0];
    }

    private int createProgram(String vertexSource, String fragmentSource) {
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
            throw new IllegalStateException("Prismal program link failed: " + log);
        }
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Prismal shader compile failed: " + log);
        }
        return shader;
    }


    private int glassUniformLocation(String name) {
        Integer cached = glassUniformLocations.get(name);
        if (cached != null) return cached;
        int location = GLES20.glGetUniformLocation(glassProgram, name);
        glassUniformLocations.put(name, location);
        return location;
    }

    private void uniform1f(String name, float value) {
        GLES20.glUniform1f(glassUniformLocation(name), value);
    }
    private void uniform1i(String name, int value) {
        GLES20.glUniform1i(glassUniformLocation(name), value);
    }
    private void uniform2f(String name, float x, float y) {
        GLES20.glUniform2f(glassUniformLocation(name), x, y);
    }
    private void uniform4f(String name, float x, float y, float z, float w) {
        GLES20.glUniform4f(glassUniformLocation(name), x, y, z, w);
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing Prismal uniform " + name);
        return location;
    }
    private static int requireAttrib(int program, String name) {
        int location = GLES20.glGetAttribLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing Prismal attribute " + name);
        return location;
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private static FloatBuffer floatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private void releaseTargets() {
        releaseReducedOutput();
        if (sourceFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{sourceFramebuffer}, 0);
        if (blurFramebufferH != 0) GLES20.glDeleteFramebuffers(1, new int[]{blurFramebufferH}, 0);
        if (blurFramebufferV != 0) GLES20.glDeleteFramebuffers(1, new int[]{blurFramebufferV}, 0);
        if (outputFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{outputFramebuffer}, 0);
        if (sourceTexture != 0) GLES20.glDeleteTextures(1, new int[]{sourceTexture}, 0);
        if (blurTextureH != 0) GLES20.glDeleteTextures(1, new int[]{blurTextureH}, 0);
        if (blurTextureV != 0) GLES20.glDeleteTextures(1, new int[]{blurTextureV}, 0);
        if (outputTexture != 0) GLES20.glDeleteTextures(1, new int[]{outputTexture}, 0);
        sourceFramebuffer = blurFramebufferH = blurFramebufferV = outputFramebuffer = 0;
        sourceTexture = blurTextureH = blurTextureV = outputTexture = 0;
        width = height = renderWidth = renderHeight = outputWidth = outputHeight = 0;
        blurWidth = blurHeight = 0;
        backdropPrepared = false;
        outputNeedsFullClear = true;
        glassFrameBegun = false;
        glassDrawCount = 0;
        glassTexturesBound = false;
        legacySingleDraw = false;
    }

    private void releaseReducedOutput() {
        if (reducedOutputFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{reducedOutputFramebuffer}, 0);
        }
        if (reducedOutputTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{reducedOutputTexture}, 0);
        }
        reducedOutputFramebuffer = reducedOutputTexture = 0;
        reducedOutputWidth = reducedOutputHeight = 0;
    }

    @Override
    public void close() {
        releaseTargets();
        if (sourceProgram != 0) GLES20.glDeleteProgram(sourceProgram);
        if (blurHProgram != 0) GLES20.glDeleteProgram(blurHProgram);
        if (blurVProgram != 0) GLES20.glDeleteProgram(blurVProgram);
        if (glassProgram != 0) GLES20.glDeleteProgram(glassProgram);
        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;
        sourcePositionLocation = sourceUvLocation = sourceTextureLocation = -1;
        blurHPositionLocation = blurHTextureLocation = blurHTexelSizeLocation = blurHSigmaLocation = -1;
        blurVPositionLocation = blurVTextureLocation = blurVTexelSizeLocation = blurVSigmaLocation = -1;
        glassPositionLocation = -1;
        cachedStaticParams = null;
        cachedHighlightMask = Integer.MIN_VALUE;
        cachedResolutionWidth = cachedResolutionHeight = -1;
        cachedBlurWidth = cachedBlurHeight = -1;
        cachedBlurSigma = Float.NaN;
        glassTexturesBound = false;
        glassUniformLocations.clear();
    }
}
