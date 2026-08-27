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

    @Test
    public void wholeDockShadowUsesMiuiNativeShadowInsteadOfSoftwareLayer() throws IOException {
        String source = mainHook();
        assertTrue("whole-Dock shadow must own the vendor Mi Shadow path",
                source.contains("installNativeDockShadowOwnership(classLoader)"));
        assertTrue("configured shadow must be applied through the vendor native shadow helper",
                source.contains("applyConfiguredNativeDockShadow"));
        assertFalse("whole-Dock shadow must not force a software View layer",
                source.contains("view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);"));
        assertFalse("whole-Dock shadow must not draw with Paint.setShadowLayer",
                source.contains("paint.setShadowLayer("));
    }

    @Test
    public void vendorShadowStateIsCapturedAndRestoredOnOwnershipRelease() throws IOException {
        String source = mainHook();
        assertTrue("vendor shadow calls must be captured before LiquidDock substitutes parameters",
                source.contains("captureVendorDockShadow(args);"));
        assertTrue("Dock customization teardown must restore exact vendor shadow parameters",
                source.contains("restoreVendorDockShadow();"));
        String disable = slice(source,
                "static void onRuntimeDockShadowDisabled()",
                "static void onRuntimeDockShadowEnabled()");
        assertTrue("shadow-only disable must clear the authoritative native target while customization still owns it",
                disable.contains("View target = nativeShadowTarget();")
                        && disable.contains("clearNativeDockShadowArgs(target)"));
        assertTrue("parent customization teardown must not be followed by a child clear",
                disable.contains("!VisualRuntimeState.isDockCustomizationEnabled()"));
        assertFalse("runtime shadow-only disable must not restore vendor state",
                disable.contains("restoreVendorDockShadow();"));
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
