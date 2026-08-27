package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts derived from HyperOS device logs showing the real native shadow boundary. */
public class DockShadowDeviceEvidenceContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void wholeDockCustomizationRewritesTheActualMiShadowBoundary() throws Exception {
        String stroke = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));

        assertTrue("device logs show MiShadowUtils.applyViewShadow is the authoritative Dock shadow boundary",
                stroke.contains("com.miui.home.launcher.common.MiShadowUtils")
                        && stroke.contains("applyViewShadow"));
        assertTrue("only the real HotSeats blur background may be rewritten",
                stroke.contains("HotSeatsListContentBlurBackground2"));
        assertTrue("Dock shadow enable must control the native color/alpha argument",
                stroke.contains("VisualRuntimeState.isDockShadowEnabled()")
                        && stroke.contains("args[1]"));
        assertTrue("configured Y offset must reach the native call",
                stroke.contains("dock.shadowY") && stroke.contains("args[3]"));
        assertTrue("configured radius/size must reach the native call",
                stroke.contains("dock.shadowRadius")
                        && stroke.contains("dock.shadowSize")
                        && stroke.contains("args[4]"));
    }

    @Test
    public void numericShadowPreferencesAreLiveRefreshInputs() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));

        assertTrue(state.contains("ConfigSchema.Dock.SHADOW_RADIUS.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.SHADOW_SIZE.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.SHADOW_ALPHA.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.SHADOW_Y.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.STROKE_SHADOW_RADIUS.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name()"));
        assertTrue("whole-Dock numeric changes must force the vendor native lifecycle to refresh",
                state.contains("MainHook.onRuntimeDockShadowEnabled()"));
        assertTrue("stroke-shadow numeric changes must refresh the installed stroke owner",
                state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }

    @Test
    public void nativeModeStrokeShadowDoesNotRequireAGlassHost() throws Exception {
        String stroke = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("native mode must remember the actual HotSeats background as a stroke owner",
                stroke.contains("KNOWN_NATIVE_HOSTS"));
        assertTrue("native fallback must derive stroke-shadow parameters from the same config",
                stroke.contains("strokeShadowRadius") && stroke.contains("strokeShadowAlpha"));
        assertFalse("native stroke-shadow fallback must not require DockLiquidGlassHostView",
                stroke.contains("DockLiquidGlassHostView"));
        assertTrue("glass mode may still install its border on the glass host",
                glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                host, config.dock, nativeRadius);"));
    }
}
