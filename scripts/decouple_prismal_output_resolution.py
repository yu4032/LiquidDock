from pathlib import Path

path = Path("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """    private int renderWidth;\n    private int renderHeight;\n    private int blurWidth;\n""",
    """    private int renderWidth;\n    private int renderHeight;\n    // Glass/SDF/highlight raster stays in the logical output pixel domain even when the\n    // captured backdrop is intentionally downsampled for bandwidth/blur cost.\n    private int outputWidth;\n    private int outputHeight;\n    private int blurWidth;\n""",
    "output dimension fields",
)

replace_once(
    """     * Geometry, u_resolution and pixel-valued optics stay in logical framebuffer pixels; only\n     * raster targets are reduced. Existing callers keep physical == logical behavior.\n""",
    """     * Geometry, u_resolution and pixel-valued optics stay in logical framebuffer pixels. Source\n     * normalization and blur may use fewer physical pixels, while the final glass/SDF/highlight\n     * raster remains at logical output resolution. Existing callers keep physical == logical behavior.\n""",
    "prepareBackdrop documentation",
)

replace_once(
    """        ensurePrograms();\n        ensureTargets(physicalWidth, physicalHeight);\n        width = logicalFramebufferWidth;\n""",
    """        ensurePrograms();\n        ensureTargets(physicalWidth, physicalHeight,\n                logicalFramebufferWidth, logicalFramebufferHeight);\n        width = logicalFramebufferWidth;\n""",
    "prepareBackdrop target sizing",
)

replace_once(
    """        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, renderWidth, renderHeight);\n        GLES20.glDisable(GLES20.GL_BLEND);\n""",
    """        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, outputWidth, outputHeight);\n        GLES20.glDisable(GLES20.GL_BLEND);\n""",
    "beginGlassFrame viewport",
)

replace_once(
    """    private void ensureTargets(int nextWidth, int nextHeight) {\n        if (renderWidth == nextWidth && renderHeight == nextHeight && outputTexture != 0) return;\n        releaseTargets();\n        renderWidth = Math.max(1, nextWidth);\n        renderHeight = Math.max(1, nextHeight);\n        blurWidth = Math.max(1, (int) (renderWidth * BLUR_FBO_SCALE));\n        blurHeight = Math.max(1, (int) (renderHeight * BLUR_FBO_SCALE));\n\n        sourceTexture = createTexture(renderWidth, renderHeight);\n        sourceFramebuffer = createFramebuffer(sourceTexture);\n        blurTextureH = createTexture(blurWidth, blurHeight);\n        blurFramebufferH = createFramebuffer(blurTextureH);\n        blurTextureV = createTexture(blurWidth, blurHeight);\n        blurFramebufferV = createFramebuffer(blurTextureV);\n        outputTexture = createTexture(renderWidth, renderHeight);\n        outputFramebuffer = createFramebuffer(outputTexture);\n    }\n""",
    """    private void ensureTargets(int nextWidth, int nextHeight) {\n        ensureTargets(nextWidth, nextHeight, nextWidth, nextHeight);\n    }\n\n    private void ensureTargets(int nextRenderWidth, int nextRenderHeight,\n                               int nextOutputWidth, int nextOutputHeight) {\n        if (renderWidth == nextRenderWidth && renderHeight == nextRenderHeight\n                && outputWidth == nextOutputWidth && outputHeight == nextOutputHeight\n                && outputTexture != 0) {\n            return;\n        }\n        releaseTargets();\n        renderWidth = Math.max(1, nextRenderWidth);\n        renderHeight = Math.max(1, nextRenderHeight);\n        outputWidth = Math.max(1, nextOutputWidth);\n        outputHeight = Math.max(1, nextOutputHeight);\n        blurWidth = Math.max(1, (int) (renderWidth * BLUR_FBO_SCALE));\n        blurHeight = Math.max(1, (int) (renderHeight * BLUR_FBO_SCALE));\n\n        sourceTexture = createTexture(renderWidth, renderHeight);\n        sourceFramebuffer = createFramebuffer(sourceTexture);\n        blurTextureH = createTexture(blurWidth, blurHeight);\n        blurFramebufferH = createFramebuffer(blurTextureH);\n        blurTextureV = createTexture(blurWidth, blurHeight);\n        blurFramebufferV = createFramebuffer(blurTextureV);\n        outputTexture = createTexture(outputWidth, outputHeight);\n        outputFramebuffer = createFramebuffer(outputTexture);\n    }\n""",
    "target allocation split",
)

replace_once(
    """        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, renderWidth, renderHeight);\n        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);\n""",
    """        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);\n        GLES20.glViewport(0, 0, outputWidth, outputHeight);\n        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);\n""",
    "glass node viewport",
)

replace_once(
    """        width = height = renderWidth = renderHeight = blurWidth = blurHeight = 0;\n""",
    """        width = height = renderWidth = renderHeight = outputWidth = outputHeight = 0;\n        blurWidth = blurHeight = 0;\n""",
    "target release reset",
)

path.write_text(text)
