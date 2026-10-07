package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Architectural boundary: Prismal is reusable GL, LiquidDock owns platform adaptation only. */
public class PrismalModuleBoundaryContractTest {
    private static final Path MODULE = Path.of("prismal/src/main");
    private static final Path APP = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void appDependsOnStandalonePrismalModule() throws Exception {
        String settings = Files.readString(Path.of("settings.gradle.kts"));
        String build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(settings.contains("include(\":prismal\")"));
        assertTrue(build.contains("implementation(project(\":prismal\"))"));
    }

    @Test
    public void officialFragmentContainsNoLiquidDockPlatformMapping() throws Exception {
        String shader = Files.readString(MODULE.resolve("res/raw/prismal_fragment.glsl"));
        assertTrue(shader.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;"));
        assertTrue(shader.contains("return clamp(scaled + offset, vec2(0.0), vec2(1.0));"));
        assertFalse(shader.contains("u_dockUvRect"));
        assertFalse(shader.contains("samplerExternalOES"));
        assertFalse(shader.contains("uTexMatrix"));
        assertFalse(shader.contains("legacySCurve"));
        assertFalse(shader.contains("LiquidDock"));
    }

    @Test
    public void portableRendererUsesOfficialFramebufferAndGlassDomains() throws Exception {
        String renderer = Files.readString(
                MODULE.resolve("java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(renderer.contains("uniform2f(\"u_resolution\", width, height)"));
        assertTrue(renderer.contains("uniform2f(\"u_mousePos\", g.centerX, height - g.centerY)"));
        assertTrue(renderer.contains("uniform2f(\"u_glassSize\", g.glassWidth, g.glassHeight)"));
        assertTrue(renderer.contains("sourceFramebuffer")
                && renderer.contains("blurFramebufferH")
                && renderer.contains("blurFramebufferV")
                && renderer.contains("outputFramebuffer"));
        assertFalse(renderer.contains("import android.graphics.SurfaceTexture"));
        assertFalse(renderer.contains("import android.view."));
        assertFalse(renderer.contains("GLES11Ext"));
        assertFalse(renderer.contains("import com.hellovoid.liquiddock"));
        assertFalse(renderer.contains("Miuix307"));
        assertFalse(renderer.contains("android.content.Context"));
        assertFalse(renderer.contains("android.content.res.Resources"));
        assertFalse(renderer.contains("openRawResource"));
        assertTrue(renderer.contains("PrismalShaderSources.FRAGMENT"));
        assertTrue(renderer.contains("glassUniformLocations"));
        assertTrue(renderer.contains("GLES20.glGetUniformLocation(glassProgram, name)"));
        assertTrue(renderer.contains("GLES20.glUniform1f(glassUniformLocation(name), value)"));
        assertFalse(renderer.contains("GLES20.glUniform1f(requireUniform(glassProgram, name), value)"));
        assertFalse(renderer.contains("requireUniform(glassProgram, \"u_backgroundTexture\")"));
    }

    @Test
    public void dockSceneOnlyFramesReusePreparedBackdrop() throws Exception {
        String view = Files.readString(APP.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("boolean consumedFreshProducerFrame = frameAvailable.getAndSet(false)"));
        assertTrue(view.contains("boolean rebuildBackdrop = consumedFreshProducerFrame"));
        assertTrue(view.contains("|| !canReusePreparedBackdrop(mapping, renderPlan)"));
        assertTrue(view.contains("if (rebuildBackdrop) {"));
        assertTrue(view.contains("rememberPreparedBackdrop(mapping, renderPlan)"));
        assertTrue(view.contains("private boolean canReusePreparedBackdrop("));
        assertTrue(view.contains("prepared.prismalParams != mapping.prismalParams"));
        assertTrue(view.contains("invalidatePreparedBackdrop();"));
        assertTrue(view.contains("scheduleSceneRender()"));
        assertTrue(view.contains("DockGlassSceneRenderPolicy.shouldRenderSceneOnlyChange("));
        assertFalse("Dock pull-out scene changes must never enqueue one stale render per pre-draw",
                view.contains("renderHandler.post(() -> drawLatestFrame(false));"));
        assertTrue("producer callbacks must join the same latest-only render queue",
                view.contains("scheduleRender(true);"));
        assertFalse("producer callbacks must not render every historical frame directly",
                view.contains("drawLatestFrame(true);"));
        assertTrue(view.contains("private void drainRenderQueue()"));
        assertTrue(view.contains("updateTexImage() latches the newest buffer"));
        assertTrue("Dock render thread should use display CPU priority",
                view.contains("Process.THREAD_PRIORITY_DISPLAY"));
        assertTrue("Dock should request EGL high-priority scheduling when supported",
                view.contains("EGL_IMG_context_priority")
                        && view.contains("EGL_CONTEXT_PRIORITY_HIGH_IMG")
                        && view.contains("eglQueryContext("));
        assertTrue("priority request must retain a default EGL fallback",
                view.contains("falling back to default priority")
                        && view.contains("requestedHighPriority ? \"HIGH\" : \"DEFAULT\""));
    }

    @Test
    public void dynamicDockMappingDoesNotRearmHeavyDiagnosticsEveryFrame() throws Exception {
        String view = Files.readString(APP.resolve("Miuix307PassBlurTextureView.java"));
        int start = view.indexOf("private void updateBackdropMapping()");
        int end = view.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String method = view.substring(start, end);
        assertFalse("per-frame Dock geometry must not re-arm Stage-B file logging",
                method.contains("stageBDiagnosticsLogged = false"));
        assertFalse("per-frame Dock geometry must not re-arm Prismal mapping file logging",
                method.contains("prismalMappingLogged = false"));
        assertTrue("Stage-B diagnostics must be gated before diagnostic computation",
                view.contains("MainHook.debugLogging && gpuBackdropActive && !stageBDiagnosticsLogged"));
        assertTrue("Prismal mapping diagnostics must be gated before string construction",
                view.contains("MainHook.debugLogging && gpuBackdropActive && !prismalMappingLogged"));
    }

    @Test
    public void prismalHotPathAvoidsRedundantDriverQueriesAndFullOverwriteClears() throws Exception {
        String renderer = Files.readString(
                MODULE.resolve("java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(renderer.contains("sourceTextureLocation = requireUniform(sourceProgram, \"uTexture\")"));
        assertTrue(renderer.contains("blurHPositionLocation = requireAttrib(blurHProgram, \"a_position\")"));
        assertTrue(renderer.contains("blurVPositionLocation = requireAttrib(blurVProgram, \"a_position\")"));

        int sourceStart = renderer.indexOf("private void renderSourceAdapter(");
        int sourceEnd = renderer.indexOf("private void renderBlur(", sourceStart);
        String source = renderer.substring(sourceStart, sourceEnd);
        assertFalse(source.contains("glGetUniformLocation"));
        assertFalse(source.contains("glGetAttribLocation"));
        assertFalse("full source overwrite must not clear first", source.contains("glClear("));

        int blurStart = renderer.indexOf("private void renderBlurPass(");
        int blurEnd = renderer.indexOf("private void renderGlassNode(", blurStart);
        String blur = renderer.substring(blurStart, blurEnd);
        assertFalse(blur.contains("glGetUniformLocation"));
        assertFalse(blur.contains("glGetAttribLocation"));
        assertFalse("full blur overwrite must not clear first", blur.contains("glClear("));

        String dock = Files.readString(APP.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(dock.contains("if (currentEglSurface == eglWindowSurface) return;"));
        int normalizeStart = dock.indexOf("private void renderNormalizationPass(");
        int normalizeEnd = dock.indexOf("private PrismalGeometry createPrismalGeometry", normalizeStart);
        String normalize = dock.substring(normalizeStart, normalizeEnd);
        assertFalse(normalize.contains("glGetUniformLocation"));
        assertFalse(normalize.contains("glGetAttribLocation"));
        assertFalse("Dock full normalization overwrite must not clear first",
                normalize.contains("glClear("));
    }

    @Test
    public void liquidDockAdapterOwnsOesNormalizationMappingLogAndFinalCrop() throws Exception {
        String view = Files.readString(APP.resolve("Miuix307PassBlurTextureView.java"));
        String composite = Files.readString(APP.resolve("Miuix307PrismalCompositeShaders.java"));
        assertTrue(view.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(view.contains("prismalRenderer.prepareBackdrop(")
                && view.contains("dockCompositor.drawFrame(")
                && view.contains("prismalRenderer.outputTexture()"));
        assertTrue(view.contains("createPrismalGeometry(mapping)"));
        assertTrue(view.contains("private volatile BackdropSnapshot backdropSnapshot"));
        assertTrue(view.contains("BackdropSnapshot mapping = backdropSnapshot"));
        assertTrue(view.contains("DockPassBlurRenderPlan.resolve(")
                && view.contains("ensureFboSizeExact(renderPlan.physicalWidth, renderPlan.physicalHeight)"));
        assertTrue(view.contains("renderPlan.logicalWidth, renderPlan.logicalHeight"));
        assertTrue(view.contains("renderNormalizationPass(mapping)"));
        assertTrue(view.contains("[DC][PRISMAL-MAP]"));
        assertTrue(view.contains("renderCompositePass(prismalTexture, mapping)"));
        assertTrue(view.contains("if (backdropSnapshot != mapping"));
        assertTrue(composite.contains("uCropRect.xy + vUv * uCropRect.zw"));
    }
}
