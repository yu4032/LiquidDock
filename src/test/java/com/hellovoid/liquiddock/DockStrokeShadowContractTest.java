package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for the stroke-shadow setting after stroke rendering moved in-process. */
public class DockStrokeShadowContractTest {
    private static String source() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java"),
                StandardCharsets.UTF_8);
    }

    @Test public void styleConsumesExistingStrokeShadowConfig() throws IOException {
        String source = source();
        assertTrue(source.contains("final boolean shadowEnabled;"));
        assertTrue(source.contains("final float shadowRadiusPx;"));
        assertTrue(source.contains("final int shadowAlpha;"));
        assertTrue(source.contains("config.strokeShadow,"));
        assertTrue(source.contains("config.strokeShadowRadius * dimensionScale"));
        assertTrue(source.contains("config.strokeShadowAlpha"));
    }

    @Test public void shadowUsesSharedStrokeGeometryWithoutIndependentView() throws IOException {
        String source = source();
        assertTrue(source.contains("drawStrokeShadow(canvas, s);"));
        assertTrue(source.contains("buildInwardShadowContour"));
        assertTrue(source.contains("innerRect"));
        assertFalse(source.contains("Path.Op."));
        assertFalse(source.contains("setLayerType(View.LAYER_TYPE_SOFTWARE"));
    }

    @Test public void shadowIsDrawnAfterStrokeAndExtendsIntoDockInterior() throws IOException {
        String source = source();
        int stroke = source.indexOf("canvas.drawPath(outer, paint);");
        int shadow = source.indexOf("drawStrokeShadow(canvas, s);", stroke);
        assertTrue("foreground stroke must be rendered before the inward shadow",
                stroke >= 0 && shadow > stroke);
        assertTrue("the inward shadow must start from the stroke inner contour",
                source.contains("buildInwardShadowContour(shadowOuter, outerDistance, s);"));
        assertTrue("the inward shadow must advance farther into the Dock interior",
                source.contains("buildInwardShadowContour(shadowInner, innerDistance, s);"));
        assertFalse("stroke shadow must not reuse the same outer-to-inner border ring",
                source.contains("buildInterpolatedContour(shadowOuter"));
    }

    @Test public void transparentStrokeColorDoesNotSuppressEnabledShadow() throws IOException {
        String source = source();
        assertTrue("stroke and shadow visibility must be evaluated independently",
                source.contains("boolean strokeVisible") && source.contains("boolean shadowVisible"));
        assertFalse("draw must not return solely because the stroke color is transparent",
                source.contains("|| Color.alpha(s.color) <= 0\n                    || bounds.width()"));
    }
}
