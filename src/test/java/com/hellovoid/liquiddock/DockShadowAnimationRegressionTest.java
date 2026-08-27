package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts for whole-Dock native shadow ownership during Dock resize animation. */
public class DockShadowAnimationRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void resizeAnimationDoesNotCreateOrReorderShadowSiblingViews() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertFalse("native whole-Dock shadow must not keep a standalone shadow View owner",
                main.contains("shadowViewRef"));
        assertFalse("native whole-Dock shadow must not create a sibling View",
                main.contains("makeDockShadow("));
        assertFalse("native whole-Dock shadow must not mutate sibling z-order",
                main.contains("ensureShadowBelowBackground("));
    }

    @Test
    public void geometryCallbacksStayShadowPassiveDuringResize() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String syncAll = slice(main,
                "private static void syncAll(View bg)",
                "static boolean isWorkstationMode()");
        String syncShadow = slice(main,
                "static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock)",
                "static void onRuntimeDockShadowDisabled()");

        assertTrue("geometry callbacks must skip shadow/config work while Dock animation is active",
                syncAll.contains("if (workstationMode || animating(bg)) return;"));
        assertTrue("settled geometry may only publish the latest background/config",
                syncAll.contains("syncDockShadow(bg, LiquidDockConfig.load().dock);"));
        assertFalse("geometry sync must not redraw HotSeats shadow",
                syncShadow.contains("refreshVendorDockShadow()"));
        assertFalse("geometry sync must not invoke terminal MiShadow directly",
                syncShadow.contains("applyViewShadow"));
        assertFalse("geometry sync must not publish speculative vendor mWidth/mHeight geometry",
                syncAll.contains("HookUtil.getIntField(bg, \"mWidth\")")
                        || syncAll.contains("HookUtil.getIntField(bg, \"mHeight\")"));
    }

    @Test
    public void temporaryHotSeatsOverridesAreRestoredAfterVendorCall() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String ownership = slice(main,
                "private static void installNativeDockShadowOwnership(",
                "private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(");

        assertTrue("vendor showViewShadow must execute inside a temporary override scope",
                ownership.contains("HotSeatsShadowScope scope = pushConfiguredHotSeatsShadow(hotSeats);")
                        && ownership.contains("scope.close();"));
        assertTrue("temporary field values must be restored after the vendor call",
                main.contains("state.field.set(target, state.value)"));
        assertFalse("old terminal-API recursion guard must not remain",
                main.contains("nativeShadowInternalCall"));
        assertFalse("old vendor MiShadow argument backup must not remain",
                main.contains("captureVendorDockShadow"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
