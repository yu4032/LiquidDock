package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for Dock body highlight and silhouette ownership. */
public class DockGlassShapeOwnershipContractTest {
    private static final Path PASS_BLUR_TEXTURE =
            Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");
    private static final Path DOCK_COMPOSITOR =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockGlassCompositor.java");
    private static final Path PRISMAL_RENDERER =
            Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java");
    private static final Path PRISMAL_HIGHLIGHT_PROFILE =
            Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalHighlightProfile.java");
    private static final Path PRISMAL_OPTICAL_EDGE =
            Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalOpticalEdgeShader.java");
    private static final Path PRISMAL_RASTER_GUARD =
            Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalRasterGuardShader.java");

    @Test
    public void dockBodyUsesConfiguredLargeSurfaceHighlightProfile() throws Exception {
        String texture = Files.readString(PASS_BLUR_TEXTURE);
        String compositor = Files.readString(DOCK_COMPOSITOR);

        assertTrue(texture.contains("PrismalHighlightProfile dockBodyHighlightProfile"));
        assertTrue(texture.contains(
                "dockBodyHighlightProfile = glassConfig.largeSurfaceHighlightProfile;"));
        assertTrue(texture.contains("dockBodyHighlightProfile, dockScene"));

        assertTrue(compositor.contains(
                "PrismalHighlightProfile dockBodyHighlightProfile"));
        assertTrue(compositor.contains(
                "renderer.drawGlass(dockBody, params, bodyHighlightProfile);"));
        assertFalse(compositor.contains("renderer.drawGlass(dockBody, params);"));
    }

    @Test
    public void dockBodyReplacesLegacySpecularAndRimWithOs4Edge() throws Exception {
        String compositor = Files.readString(DOCK_COMPOSITOR);
        String profile = Files.readString(PRISMAL_HIGHLIGHT_PROFILE);
        String opticalEdge = Files.readString(PRISMAL_OPTICAL_EDGE);
        String renderer = Files.readString(PRISMAL_RENDERER);

        assertTrue(profile.contains("withOs4EdgeReplacingLegacyEdge"));
        assertTrue(profile.contains("public final boolean os4Edge;"));
        assertTrue(compositor.contains(
                "bodyHighlightProfile = bodyHighlightProfile.withOs4EdgeReplacingLegacyEdge();"));
        assertTrue(opticalEdge.contains("uniform float u_os4EdgeEnabled;"));
        assertTrue(opticalEdge.contains("vec3 os4EdgeNormal3"));
        assertTrue(opticalEdge.contains("os4EdgeReflection"));
        assertTrue(renderer.contains(
                "uniform1f(\"u_os4EdgeEnabled\", highlights.os4Edge ? 1f : 0f);"));
    }

    @Test
    public void prismalRasterCoversTheOuterHalfOfTheSdfAntialiasBand() throws Exception {
        String renderer = Files.readString(PRISMAL_RENDERER);

        assertTrue(renderer.contains(
                "PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX)"));
        assertTrue(Files.exists(PRISMAL_RASTER_GUARD));
        String guard = Files.readString(PRISMAL_RASTER_GUARD);
        assertTrue(guard.contains("vec2 rasterScale = (safeGlassSize + vec2(4.0)) / safeGlassSize;"));
        assertTrue(guard.contains("vec2 rasterPosition = a_position * rasterScale;"));
        assertTrue(guard.contains("v_shapeCoord = rasterPosition;"));
    }
}
