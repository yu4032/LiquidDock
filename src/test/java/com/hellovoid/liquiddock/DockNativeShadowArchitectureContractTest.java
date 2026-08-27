package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Architecture contract for the whole-Dock shadow on HyperOS 3. */
public class DockNativeShadowArchitectureContractTest {
    private static String mainHook() throws Exception {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
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
    public void wholeDockShadowUsesHotSeatsNativeLifecycle() throws Exception {
        String main = mainHook();
        assertTrue("HotSeats.showViewShadow must remain the native shadow renderer",
                main.contains("getDeclaredMethod(\"showViewShadow\")"));
        assertTrue("HotSeats translation lifecycle must remain vendor-owned",
                main.contains("getDeclaredMethod(\"setTranslationY\", float.class)"));
        assertTrue("LiquidDock may only wrap the vendor call with temporary state",
                main.contains("HotSeatsShadowScope scope = pushConfiguredHotSeatsShadow(hotSeats);"));
        assertFalse("whole-Dock ownership must not hook terminal MiShadowUtils.applyViewShadow",
                main.contains("HookUtil.hookMethod(ms, \"applyViewShadow\""));
        assertFalse("whole-Dock ownership must not retain a second native target",
                main.contains("nativeShadowTargetRef"));
    }

    @Test
    public void configuredNativeShadowKeepsRadiusCapYOffsetAndVendorAlphaPath() throws Exception {
        String main = mainHook();
        assertTrue("native radius must retain the min(radius,size) cap",
                main.contains("Math.max(0f, dock.shadowRadius * scale)")
                        && main.contains("Math.max(0f, dock.shadowSize * scale)"));
        assertTrue("configured whole-Dock shadow must retain the Y-offset setting",
                main.contains("float offsetYPx = dock.shadowY * scale;"));
        assertTrue("configured whole-Dock shadow must retain configured alpha",
                main.contains("dock.shadowAlpha"));
        assertTrue("alpha customization must stay inside the vendor MI_SHADOW_ALPHA path",
                main.contains("MI_SHADOW_ALPHA"));
    }
}
