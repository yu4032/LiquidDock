package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for whole-Dock native shadow ownership. */
public class DockShadowRegressionContractTest {
    private static String mainHook() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    private static String shadowBridge() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockNativeShadowBridge.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void wholeDockShadowUsesVendorHotSeatsInsteadOfSoftwareLayer() throws IOException {
        String source = mainHook();
        assertTrue("whole-Dock shadow must hook the vendor HotSeats shadow lifecycle",
                source.contains("installNativeDockShadowOwnership(classLoader)"));
        assertTrue("configured shadow must be rendered by HotSeats.showViewShadow",
                source.contains("getDeclaredMethod(\"showViewShadow\")"));
        assertFalse("whole-Dock shadow must not force a software View layer",
                source.contains("view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);"));
        assertFalse("whole-Dock shadow must not draw with Paint.setShadowLayer",
                source.contains("paint.setShadowLayer("));
        assertFalse("whole-Dock shadow must not maintain a terminal native target",
                source.contains("nativeShadowTargetRef"));
    }

    @Test
    public void runtimeOwnershipRestoresOnlyNumericVendorStateAndAlphaStaysAtTerminalBoundary() throws IOException {
        String source = mainHook();
        String bridge = shadowBridge();
        String configured = slice(source,
                "private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(",
                "private static LiquidDockConfig.Dock currentNativeShadowConfig()");
        String disable = slice(source,
                "static void onRuntimeDockShadowDisabled()",
                "static void onRuntimeDockShadowEnabled()");
        String customizationDisable = slice(source,
                "static void onRuntimeDockCustomizationDisabled()",
                "private static void installDockResizeAnimationBypass(");

        assertFalse("HotSeats temporary state must never carry alpha on Launcher 4.50",
                configured.contains("dock.shadowAlpha") || configured.contains("overrideViewAlpha(")
                        || configured.contains("MI_SHADOW_ALPHA"));
        assertTrue("shadow-only disable must resolve to zero alpha at terminal MiShadow rewrite",
                bridge.contains("int dockAlpha = dockShadow ? clamp255(dock.shadowAlpha) : 0;")
                        && bridge.contains("args[1] = Color.argb(outAlpha,"));
        assertTrue("temporary numeric vendor fields must be restored after each vendor call",
                source.contains("state.field.set(target, state.value)"));
        assertTrue("runtime shadow-only disable must ask HotSeats to redraw itself",
                disable.contains("refreshVendorDockShadow();"));
        assertTrue("full customization release must ask HotSeats to redraw using untouched lifecycle state",
                customizationDisable.contains("refreshVendorDockShadow();"));
        assertFalse("scoped ownership must not manufacture a vendor backup replay",
                source.contains("restoreVendorDockShadow"));
    }

    @Test
    public void zeroCopyGlassHostMovesWithVendorDockAsItsChild() throws IOException {
        String main = mainHook();
        String hook = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java"),
                StandardCharsets.UTF_8);
        assertFalse("zero-copy host is a child of the vendor Dock and must not duplicate parent translation",
                main.contains("liquidGlassHostView.setTranslationX")
                        || main.contains("liquidGlassHostView.setTranslationY"));
        assertTrue("zero-copy host must be attached directly to the vendor material host",
                hook.contains("materialHost.addView(host, materialHost.getChildCount(), hostLp);"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) return "";
        return source.substring(from, to);
    }
}
