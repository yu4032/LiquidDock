from pathlib import Path


def replace_once(text: str, anchor: str, replacement: str, name: str) -> str:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"{name}: expected exactly one anchor, found {count}")
    return text.replace(anchor, replacement)


renderer_path = Path("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java")
text = renderer_path.read_text()

text = replace_once(
    text,
    "    private int glassProgram;\n",
    "    private int glassProgram;\n"
    "    private int bloomMaskProgram;\n"
    "    private int bloomCompositeProgram;\n",
    "program fields",
)

text = replace_once(
    text,
    "    private int outputFramebuffer;\n",
    "    private int outputFramebuffer;\n"
    "    private int bloomMaskTexture;\n"
    "    private int bloomMaskFramebuffer;\n"
    "    private int bloomBlurTextureH;\n"
    "    private int bloomBlurFramebufferH;\n"
    "    private int bloomBlurTextureV;\n"
    "    private int bloomBlurFramebufferV;\n"
    "    private int bloomTargetWidth;\n"
    "    private int bloomTargetHeight;\n",
    "bloom target fields",
)

old_program_guard = """        if (sourceProgram != 0 && blurHProgram != 0 && blurVProgram != 0 && glassProgram != 0) {
            return;
        }
"""
new_program_guard = """        if (sourceProgram != 0 && blurHProgram != 0 && blurVProgram != 0 && glassProgram != 0
                && bloomMaskProgram != 0 && bloomCompositeProgram != 0) {
            return;
        }
"""
text = replace_once(text, old_program_guard, new_program_guard, "program guard")

old_program_create = """        glassProgram = createProgram(PrismalShaderSources.VERTEX, glassFragment);
        glassUniformLocations.clear();
"""
new_program_create = """        glassProgram = createProgram(PrismalShaderSources.VERTEX, glassFragment);
        bloomMaskProgram = createProgram(PrismalBloomShaderSources.MASK_VERTEX,
                PrismalBloomShaderSources.MASK_FRAGMENT);
        bloomCompositeProgram = createProgram(PrismalBloomShaderSources.COMPOSITE_VERTEX,
                PrismalBloomShaderSources.COMPOSITE_FRAGMENT);
        glassUniformLocations.clear();
"""
text = replace_once(text, old_program_create, new_program_create, "program creation")

old_program_check = """        if (sourceProgram == 0 || blurHProgram == 0 || blurVProgram == 0 || glassProgram == 0) {
            throw new IllegalStateException("Prismal shader program creation failed");
        }
"""
new_program_check = """        if (sourceProgram == 0 || blurHProgram == 0 || blurVProgram == 0 || glassProgram == 0
                || bloomMaskProgram == 0 || bloomCompositeProgram == 0) {
            throw new IllegalStateException("Prismal shader program creation failed");
        }
"""
text = replace_once(text, old_program_check, new_program_check, "program validation")

render_source_anchor = "    private void renderSourceAdapter(int inputTexture) {\n"
ensure_bloom_targets = """    private void ensureBloomTargets(int requestedWidth, int requestedHeight) {
        int safeWidth = Math.max(1, requestedWidth);
        int safeHeight = Math.max(1, requestedHeight);
        if (bloomMaskTexture != 0
                && bloomTargetWidth >= safeWidth && bloomTargetHeight >= safeHeight) {
            return;
        }

        int nextWidth = Math.max(bloomTargetWidth, safeWidth);
        int nextHeight = Math.max(bloomTargetHeight, safeHeight);
        releaseBloomTargets();
        bloomTargetWidth = nextWidth;
        bloomTargetHeight = nextHeight;
        bloomMaskTexture = createTexture(bloomTargetWidth, bloomTargetHeight);
        bloomMaskFramebuffer = createFramebuffer(bloomMaskTexture);
        bloomBlurTextureH = createTexture(bloomTargetWidth, bloomTargetHeight);
        bloomBlurFramebufferH = createFramebuffer(bloomBlurTextureH);
        bloomBlurTextureV = createTexture(bloomTargetWidth, bloomTargetHeight);
        bloomBlurFramebufferV = createFramebuffer(bloomBlurTextureV);
    }

"""
text = replace_once(
    text,
    render_source_anchor,
    ensure_bloom_targets + render_source_anchor,
    "ensure bloom targets insertion",
)

old_glass_tail = """        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void bindInterleavedQuad(int program) {
"""
new_glass_tail = """        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);

        if (p.os4BloomEnabled && p.os4BloomIntensity > 0f && opacity > 0f) {
            renderBloomOverlay(g, p, opacity);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void renderBloomOverlay(PrismalGeometry g, PrismalParams p, float opacity) {
        float bloomWidthPx = effectiveOs4BloomWidthPx(g, p);
        float bloomBlurRadiusPx = effectiveOs4BloomBlurRadiusPx(p);
        PrismalBloomTarget.Spec spec = PrismalBloomTarget.plan(
                g.glassWidth, g.glassHeight,
                bloomWidthPx, bloomBlurRadiusPx,
                outputWidth, outputHeight);
        ensureBloomTargets(spec.width, spec.height);

        renderBloomMask(g, p, bloomWidthPx, opacity);
        renderBloomBlurPass(blurHProgram, bloomMaskTexture, bloomBlurFramebufferH,
                bloomBlurRadiusPx);
        renderBloomBlurPass(blurVProgram, bloomBlurTextureH, bloomBlurFramebufferV,
                bloomBlurRadiusPx);
        renderBloomComposite(g);
    }

    private float effectiveOs4BloomWidthPx(PrismalGeometry g, PrismalParams p) {
        if (Float.isFinite(p.os4BloomWidthPx) && p.os4BloomWidthPx > 0f) {
            return p.os4BloomWidthPx;
        }
        float minDim = Math.max(1f, Math.min(g.glassWidth, g.glassHeight));
        return clamp(minDim * 0.090f, 9f, 28f);
    }

    private float effectiveOs4BloomBlurRadiusPx(PrismalParams p) {
        if (!Float.isFinite(p.os4BloomBlurRadiusPx) || p.os4BloomBlurRadiusPx <= 0f) {
            return 0.5f;
        }
        return Math.max(0.5f, p.os4BloomBlurRadiusPx);
    }

    private void renderBloomMask(PrismalGeometry g, PrismalParams p,
                                 float bloomWidthPx, float opacity) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomMaskFramebuffer);
        GLES20.glViewport(0, 0, bloomTargetWidth, bloomTargetHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(bloomMaskProgram);
        bindInterleavedQuad(bloomMaskProgram);

        GLES20.glUniform2f(requireUniform(bloomMaskProgram, "u_targetSize"),
                bloomTargetWidth, bloomTargetHeight);
        GLES20.glUniform2f(requireUniform(bloomMaskProgram, "u_glassSize"),
                g.glassWidth, g.glassHeight);
        GLES20.glUniform4f(requireUniform(bloomMaskProgram, "u_cornerRadii"),
                g.topLeftRadius, g.topRightRadius, g.bottomRightRadius, g.bottomLeftRadius);
        GLES20.glUniform1f(requireUniform(bloomMaskProgram, "u_bloomWidthPx"), bloomWidthPx);
        float bloomIntensity = Float.isFinite(p.os4BloomIntensity)
                ? Math.max(0f, p.os4BloomIntensity) : 0f;
        float rimStrength = Float.isFinite(p.rimStrength) ? Math.max(0f, p.rimStrength) : 0f;
        GLES20.glUniform1f(requireUniform(bloomMaskProgram, "u_bloomIntensity"),
                bloomIntensity * rimStrength * opacity);
        GLES20.glUniform2f(requireUniform(bloomMaskProgram, "u_lightDir"),
                p.lightDirX, p.lightDirY);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindInterleavedQuad(bloomMaskProgram);
    }

    private void renderBloomBlurPass(int program, int inputTexture, int framebuffer,
                                     float sigma) {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, bloomTargetWidth, bloomTargetHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(program);
        int position = requireAttrib(program, "a_position");
        blurQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, blurQuad);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture);
        GLES20.glUniform1i(requireUniform(program, "u_texture"), 0);
        GLES20.glUniform2f(requireUniform(program, "u_texelSize"),
                1f / Math.max(1, bloomTargetWidth), 1f / Math.max(1, bloomTargetHeight));
        GLES20.glUniform1f(requireUniform(program, "u_sigma"), Math.max(0.5f, sigma));
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
    }

    private void renderBloomComposite(PrismalGeometry g) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outputFramebuffer);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(bloomCompositeProgram);
        bindInterleavedQuad(bloomCompositeProgram);
        GLES20.glUniform2f(requireUniform(bloomCompositeProgram, "u_resolution"), width, height);
        GLES20.glUniform2f(requireUniform(bloomCompositeProgram, "u_centerPx"),
                g.centerX, height - g.centerY);
        GLES20.glUniform2f(requireUniform(bloomCompositeProgram, "u_targetSize"),
                bloomTargetWidth, bloomTargetHeight);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomBlurTextureV);
        GLES20.glUniform1i(requireUniform(bloomCompositeProgram, "uTexture"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindInterleavedQuad(bloomCompositeProgram);
    }

    private void bindInterleavedQuad(int program) {
"""
text = replace_once(text, old_glass_tail, new_glass_tail, "glass tail and bloom pipeline")

release_targets_anchor = "    private void releaseTargets() {\n"
release_bloom_targets = """    private void releaseBloomTargets() {
        if (bloomMaskFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{bloomMaskFramebuffer}, 0);
        if (bloomBlurFramebufferH != 0) GLES20.glDeleteFramebuffers(1, new int[]{bloomBlurFramebufferH}, 0);
        if (bloomBlurFramebufferV != 0) GLES20.glDeleteFramebuffers(1, new int[]{bloomBlurFramebufferV}, 0);
        if (bloomMaskTexture != 0) GLES20.glDeleteTextures(1, new int[]{bloomMaskTexture}, 0);
        if (bloomBlurTextureH != 0) GLES20.glDeleteTextures(1, new int[]{bloomBlurTextureH}, 0);
        if (bloomBlurTextureV != 0) GLES20.glDeleteTextures(1, new int[]{bloomBlurTextureV}, 0);
        bloomMaskFramebuffer = bloomBlurFramebufferH = bloomBlurFramebufferV = 0;
        bloomMaskTexture = bloomBlurTextureH = bloomBlurTextureV = 0;
        bloomTargetWidth = bloomTargetHeight = 0;
    }

"""
text = replace_once(
    text,
    release_targets_anchor,
    release_bloom_targets + "    private void releaseTargets() {\n        releaseBloomTargets();\n",
    "release bloom targets insertion",
)

old_close = """        if (blurVProgram != 0) GLES20.glDeleteProgram(blurVProgram);
        if (glassProgram != 0) GLES20.glDeleteProgram(glassProgram);
        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;
        glassUniformLocations.clear();
"""
new_close = """        if (blurVProgram != 0) GLES20.glDeleteProgram(blurVProgram);
        if (glassProgram != 0) GLES20.glDeleteProgram(glassProgram);
        if (bloomMaskProgram != 0) GLES20.glDeleteProgram(bloomMaskProgram);
        if (bloomCompositeProgram != 0) GLES20.glDeleteProgram(bloomCompositeProgram);
        sourceProgram = blurHProgram = blurVProgram = glassProgram = 0;
        bloomMaskProgram = bloomCompositeProgram = 0;
        glassUniformLocations.clear();
"""
text = replace_once(text, old_close, new_close, "close bloom programs")

renderer_path.write_text(text)

shader_path = Path("prismal/src/main/java/com/hellovoid/prismal/PrismalBloomShaderSources.java")
shader = shader_path.read_text()
old_composite = """            void main() {
                gl_FragColor = texture2D(uTexture, vUv);
            }
"""
new_composite = """            void main() {
                vec4 bloom = texture2D(uTexture, vUv);
                vec3 straightRgb = bloom.a > 1e-5 ? bloom.rgb / bloom.a : vec3(0.0);
                gl_FragColor = vec4(straightRgb, bloom.a);
            }
"""
shader = replace_once(shader, old_composite, new_composite, "bloom composite alpha conversion")
shader_path.write_text(shader)
