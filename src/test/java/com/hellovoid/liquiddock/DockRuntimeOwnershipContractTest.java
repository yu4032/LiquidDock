package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for live Dock customization, shadow and stroke ownership. */
public class DockRuntimeOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void runtimeDisableTransitionsDispatchToDockOwners() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));
        assertTrue(state.contains("MainHook.onRuntimeDockCustomizationDisabled()"));
        assertTrue(state.contains("DockStrokeRenderer.onRuntimeStrokeDisabled()"));
        assertTrue(state.contains("MainHook.onRuntimeDockShadowDisabled()"));
        assertTrue(state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }

    @Test
    public void permanentDockHooksPassThroughWhenCustomizationIsLiveDisabled() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String nativeShadow = methodSlice(main,
                "private static void installNativeDockShadowOwnership(",
                "/** Re-apply the configured visible shadow");

        assertTrue(nativeShadow.contains("VisualRuntimeState.isDockCustomizationEnabled()"));
        assertTrue(nativeShadow.contains("return chain.proceed(args);"));
        assertFalse(main.contains("if (dockCustomization) {\n            installNativeDockShadowOwnership"));
        assertTrue(main.contains("static void onRuntimeDockCustomizationDisabled()"));
        assertTrue(main.contains("restoreVendorDockShadow();"));
    }

    @Test
    public void dockShadowCannotBeResurrectedAfterLiveDisable() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String sync = methodSlice(main, "static void syncDockShadow(",
                "static void onRuntimeDockShadowDisabled()");

        assertTrue(sync.contains("VisualRuntimeState.isDockShadowEnabled()"));
        assertTrue(sync.contains("clearConfiguredNativeDockShadow();"));
        assertTrue(main.contains("static void onRuntimeDockShadowDisabled()"));
        assertTrue(main.contains("clearNativeDockShadowArgs"));
        assertFalse("disabled whole-Dock shadow must not be recreated as a separate View",
                main.contains("makeDockShadow("));
    }

    @Test
    public void strokeAndStrokeShadowUseLiveStateAndHaveImmediateTeardown() throws Exception {
        String stroke = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));

        assertTrue(stroke.contains("VisualRuntimeState.isDockStrokeEnabled()"));
        assertTrue(stroke.contains("VisualRuntimeState.isStrokeShadowEnabled()"));
        assertTrue(stroke.contains("static void onRuntimeStrokeDisabled()"));
        assertTrue(stroke.contains("static void refreshInstalledFromCurrentConfig()"));
        assertTrue(stroke.contains("installed.baseForeground()"));
        assertTrue(stroke.contains("INSTALLED.clear()"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, Math.max(0, start));
        if (start < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
