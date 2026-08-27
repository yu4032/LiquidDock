package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for live Dock customization, native shadow and stroke ownership. */
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
    public void nativeShadowHookKeepsVendorMethodAsTheActualRenderer() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String nativeShadow = methodSlice(main,
                "private static void installNativeDockShadowOwnership(",
                "private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(");

        assertTrue(nativeShadow.contains("\"showViewShadow\""));
        assertTrue(nativeShadow.contains("\"setTranslationY\""));
        assertTrue(nativeShadow.contains("chain.proceed("));
        assertFalse("terminal MiShadow API must not become a second lifecycle authority",
                nativeShadow.contains("\"applyViewShadow\""));
    }

    @Test
    public void dockShadowLiveDisableUsesVendorAlphaPathInsteadOfCreatingAnotherOwner() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String configured = methodSlice(main,
                "private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(",
                "private static LiquidDockConfig.Dock currentNativeShadowConfig()");

        assertTrue(configured.contains("VisualRuntimeState.isDockShadowEnabled()"));
        assertTrue("disabled shadow must feed zero alpha into HotSeats' own lifecycle",
                configured.contains(": 0;"));
        assertFalse(main.contains("nativeShadowTargetRef"));
        assertFalse(main.contains("customShadowTargetRef"));
        assertFalse(main.contains("makeDockShadow("));
    }

    @Test
    public void strokeDisableRetainsWeakOwnerSoFalseToTrueCanReattach() throws Exception {
        String stroke = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String disable = methodSlice(stroke,
                "static void onRuntimeStrokeDisabled()",
                "static void refreshInstalledFromCurrentConfig()");
        String refresh = methodSlice(stroke,
                "static void refreshInstalledFromCurrentConfig()",
                "private static void applyNativeOuterShadow(");

        assertTrue(stroke.contains("VisualRuntimeState.isDockStrokeEnabled()"));
        assertTrue(stroke.contains("VisualRuntimeState.isStrokeShadowEnabled()"));
        assertTrue(disable.contains("installed.baseForeground()"));
        assertFalse("live disable must not destroy the weak installation record",
                disable.contains("INSTALLED.clear()"));
        assertTrue("live re-enable must reattach the existing StrokeDrawable",
                refresh.contains("host.setForeground(installed)"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, Math.max(0, start));
        if (start < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
