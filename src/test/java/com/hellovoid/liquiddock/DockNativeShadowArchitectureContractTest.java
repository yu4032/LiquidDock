package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Architecture contract for the whole-Dock shadow on HyperOS 3 / Launcher 4.50. */
public class DockNativeShadowArchitectureContractTest {
    private static String mainHook() throws Exception {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    private static String shadowBridge() throws Exception {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockNativeShadowBridge.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void wholeDockShadowDoesNotCreateSoftwareSibling() throws Exception {
        String main = mainHook();
        assertFalse("whole-Dock shadow must not allocate a standalone shadow View",
                main.contains("private static View makeDockShadow("));
        assertFalse("whole-Dock shadow must not force a software layer",
                main.contains("view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);"));
        assertFalse("whole-Dock shadow must not use Paint.setShadowLayer",
                main.contains("paint.setShadowLayer("));
    }

    @Test
    public void wholeDockShadowKeepsVendorLifecycleAndTerminalParameterBridgeSeparate() throws Exception {
        String main = mainHook();
        String bridge = shadowBridge();
        assertTrue("HotSeats.showViewShadow must remain the native shadow lifecycle owner",
                main.contains("getDeclaredMethod(\"showViewShadow\")"));
        assertTrue("HotSeats translation lifecycle must remain vendor-owned",
                main.contains("getDeclaredMethod(\"setTranslationY\", float.class)"));
        assertTrue("LiquidDock may only wrap the vendor lifecycle with temporary numeric state",
                main.contains("HotSeatsShadowScope scope = pushConfiguredHotSeatsShadow(hotSeats);"));
        assertTrue("terminal MiShadow API may only be a parameter rewrite boundary",
                bridge.contains("HookUtil.hookMethod(utils, \"applyViewShadow\"")
                        && bridge.contains("rewrite(args);")
                        && bridge.contains("return chain.proceed(args);"));
        assertFalse("whole-Dock lifecycle ownership must not retain a second native target",
                main.contains("nativeShadowTargetRef"));
    }

    @Test
    public void configuredNativeShadowKeepsRadiusCapYOffsetAndAlphaAtTerminalBoundary() throws Exception {
        String main = mainHook();
        String bridge = shadowBridge();
        assertTrue("native radius must retain the min(radius,size) cap",
                main.contains("Math.max(0f, dock.shadowRadius * scale)")
                        && main.contains("Math.max(0f, dock.shadowSize * scale)"));
        assertTrue("configured whole-Dock shadow must retain the Y-offset setting",
                main.contains("float offsetYPx = dock.shadowY * scale;"));
        assertTrue("configured alpha must be rewritten only at the terminal shadow API",
                bridge.contains("int dockAlpha = dockShadow ? clamp255(dock.shadowAlpha) : 0;")
                        && bridge.contains("args[1] = Color.argb(outAlpha,"));
        assertFalse("Launcher 4.50 HotSeats has no dedicated shadow-alpha field; MainHook must not probe one",
                main.contains("mMiShadowAlpha") || main.contains("mShadowAlpha")
                        || main.contains("MI_SHADOW_ALPHA") || main.contains("overrideViewAlpha("));
    }
}
