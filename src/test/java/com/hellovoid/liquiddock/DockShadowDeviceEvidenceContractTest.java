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
        String bridge = Files.readString(MAIN.resolve("DockNativeShadowBridge.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue("device logs show MiShadowUtils.applyViewShadow is the authoritative Dock shadow boundary",
                bridge.contains("com.miui.home.launcher.common.MiShadowUtils")
                        && bridge.contains("applyViewShadow"));
        assertTrue("only the real HotSeats blur background may be rewritten",
                bridge.contains("HotSeatsListContentBlurBackground2"));
        assertTrue("Dock shadow enable must control the native color/alpha argument",
                bridge.contains("VisualRuntimeState.isDockShadowEnabled()")
                        && bridge.contains("args[1]"));
        assertTrue("configured Y offset must reach the native call",
                bridge.contains("dock.shadowY") && bridge.contains("args[3]"));
        assertTrue("configured radius/size must reach the native call",
                bridge.contains("dock.shadowRadius")
                        && bridge.contains("dock.shadowSize")
                        && bridge.contains("args[4]"));
        assertTrue("the final-boundary bridge must be installed in Launcher",
                module.contains("DockNativeShadowBridge.install(classLoader, runtimeConfig.dock)"));
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
        assertTrue("native shadow config must be refreshed before asking vendor lifecycle to redraw",
                state.contains("DockNativeShadowBridge.refreshConfig()")
                        && state.contains("MainHook.onRuntimeDockShadowEnabled()"));
        assertTrue("stroke-shadow numeric changes must refresh the installed stroke owner",
                state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }

    @Test
    public void nativeModeStrokeShadowDoesNotRequireAGlassHost() throws Exception {
        String stroke = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String bridge = Files.readString(MAIN.resolve("DockNativeShadowBridge.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("native mode must remember the actual HotSeats background as a stroke owner",
                stroke.contains("KNOWN_NATIVE_HOSTS"));
        assertTrue("native fallback must derive stroke-shadow parameters from the same config",
                bridge.contains("strokeShadowRadius") && bridge.contains("strokeShadowAlpha"));
        assertFalse("native stroke-shadow fallback must not require DockLiquidGlassHostView",
                bridge.contains("DockLiquidGlassHostView"));
        assertTrue("glass mode may still install its border on the glass host",
                glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                host, config.dock, nativeRadius);"));
    }
}
