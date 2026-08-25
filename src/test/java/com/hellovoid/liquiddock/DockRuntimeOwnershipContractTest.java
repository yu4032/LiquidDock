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
                "private static void installNativeDockShadowSuppression(",
                "/** Install the independent whole-Dock shadow");

        assertTrue(nativeShadow.contains("VisualRuntimeState.isDockCustomizationEnabled()"));
        assertTrue(main.contains("if (!VisualRuntimeState.isDockCustomizationEnabled())"));
        assertFalse(main.contains("if (dockCustomization) {\n            installNativeDockShadowSuppression"));
        assertTrue(main.contains("static void onRuntimeDockCustomizationDisabled()"));
    }

    @Test
    public void dockShadowCannotBeResurrectedAfterLiveDisable() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String sync = methodSlice(main, "static void syncDockShadow(",
                "/** Keep the reusable shadow below");
        String syncAll = methodSlice(main, "private static void syncAll(",
                "static boolean isWorkstationMode()");

        assertTrue(sync.contains("VisualRuntimeState.isDockShadowEnabled()"));
        assertTrue(sync.contains("removeDockShadow()"));
        assertTrue(syncAll.contains("VisualRuntimeState.isDockShadowEnabled()"));
        assertTrue(main.contains("static void onRuntimeDockShadowDisabled()"));
        assertTrue(main.contains("private static void removeDockShadow()"));
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
