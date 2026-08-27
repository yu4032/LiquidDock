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
    public void wholeDockShadowUsesMiuiNativeShadowOwnership() throws Exception {
        String main = mainHook();
        assertTrue("Dock shadow must be applied through MIUI's native shadow target",
                main.contains("applyConfiguredNativeDockShadow"));
        assertTrue("vendor shadow inputs must be captured before LiquidDock overrides them",
                main.contains("captureVendorDockShadow"));
        assertTrue("leaving Dock customization must restore captured vendor shadow inputs",
                main.contains("restoreVendorDockShadow"));
    }

    @Test
    public void configuredNativeShadowKeepsExistingRadiusCapAndYOffset() throws Exception {
        String main = mainHook();
        assertTrue("native blur radius must retain the existing min(radius, size) cap",
                main.contains("Math.min(shadowRadiusPx, shadowSizePx)"));
        assertTrue("configured whole-Dock shadow must retain the Y-offset setting",
                main.contains("dock.shadowY"));
        assertTrue("configured whole-Dock shadow must retain configured alpha in the color",
                main.contains("dock.shadowAlpha"));
    }
}
