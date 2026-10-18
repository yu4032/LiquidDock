package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** One GL pass owns Dock highlights and side shading; native MiShadow retains its bounds. */
public class DockSideEdgeOwnershipContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void dockBodyAloneUsesDirectionalEdgeInSameScene() throws Exception {
        String compositor = read("DockGlassCompositor.java");
        assertTrue(compositor.contains("renderer.setDockSideShade(DockDirectionalSideEdgeState.alpha()"));
        assertTrue(compositor.contains("renderer.drawGlass(dockBody, params, bodyHighlightProfile);"));
        assertTrue(compositor.contains("renderer.setDockSideShade(0f, 0f, 0f);"));
        assertTrue(compositor.contains("renderer.drawGlass(new PrismalGeometry"));
        assertFalse(compositor.contains("renderSideShadowOverlay"));
        assertFalse(compositor.contains("beginOverlayFrame"));
    }

    @Test public void nativeWholeDockMiShadowIsUnchanged() throws Exception {
        String nativeBridge = read("DockNativeShadowBridge.java");
        assertTrue(nativeBridge.contains("args[3] = outY;"));
        assertTrue(nativeBridge.contains("args[4] = outRadius;"));
        assertFalse(nativeBridge.contains("setLightDirection"));
        assertFalse(nativeBridge.contains("args[2] ="));
    }

    @Test public void visualSliderTriggersCachedSceneRedrawNotProducerCapture() throws Exception {
        String runtime = read("VisualRuntimeState.java");
        assertTrue(runtime.contains("DockDirectionalSideEdgeState.refresh(sharedPreferences);"));
        assertTrue(runtime.contains("Miuix307ZeroCopyRenderer.requestDockSceneRefresh();"));
        assertFalse(runtime.contains("ScreenCapture"));
    }
}
