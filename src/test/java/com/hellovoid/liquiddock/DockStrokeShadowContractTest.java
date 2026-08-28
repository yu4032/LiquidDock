package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for stroke shadow: it must remain outside the Dock content. */
public class DockStrokeShadowContractTest {
    private static String renderer() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java"),
                StandardCharsets.UTF_8);
    }

    @Test public void styleConsumesExistingStrokeShadowConfig() throws IOException {
        String source = renderer();
        assertTrue(source.contains("final boolean shadowEnabled;"));
        assertTrue(source.contains("final float shadowRadiusPx;"));
        assertTrue(source.contains("final int shadowAlpha;"));
        assertTrue(source.contains("config.strokeShadow,"));
        assertTrue(source.contains("config.strokeShadowRadius * dimensionScale"));
        assertTrue(source.contains("config.strokeShadowAlpha"));
    }

    @Test public void shadowNeverPaintsAnInwardBandOverDockContent() throws IOException {
        String source = renderer();
        assertFalse("stroke shadow must not advance from innerRect into the Dock body",
                source.contains("buildInwardShadowContour"));
        assertFalse("stroke shadow must not be painted as interior contour bands",
                source.contains("drawStrokeShadow(canvas, s);"));
        assertFalse(source.contains("setLayerType(View.LAYER_TYPE_SOFTWARE"));
    }

    @Test public void shadowUsesHardwareOuterShadowOnTheGlassHost() throws Exception {
        String source = renderer();
        String host = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java"),
                StandardCharsets.UTF_8);
        assertTrue("stroke shadow must use a hardware Mi Shadow path",
                source.contains("applyNativeOuterShadow"));
        assertTrue("the custom glass host must expose its shape to RenderNode shadow geometry",
                host.contains("setOutlineProvider"));
        assertTrue("the host outline must be invalidated when geometry changes",
                host.contains("invalidateOutline()"));
    }

    @Test public void transparentStrokeColorDoesNotSuppressEnabledOuterShadow() throws IOException {
        String source = renderer();
        assertTrue("stroke and shadow visibility remain independent",
                source.contains("shadowEnabled") && source.contains("shadowAlpha"));
        assertFalse("outer shadow lifetime must not be gated by stroke color alpha",
                source.contains("Color.alpha(s.color) <= 0 && !s.shadowEnabled"));
    }
}
