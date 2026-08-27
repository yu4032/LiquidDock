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
    public void geometryCallbacksDoNotReapplyNativeShadowMidAnimation() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String sync = slice(main,
                "private static void syncAll(View bg)",
                "static boolean isWorkstationMode()");

        assertTrue("geometry callbacks must skip native shadow writes while Dock animation is active",
                sync.contains("if (workstationMode || animating(bg)) return;"));
        assertTrue("settled geometry callbacks may refresh the configured native shadow",
                sync.contains("syncDockShadow(bg, LiquidDockConfig.load().dock);"));
        assertFalse("native shadow must not publish speculative vendor mWidth/mHeight geometry",
                sync.contains("HookUtil.getIntField(bg, \"mWidth\")")
                        || sync.contains("HookUtil.getIntField(bg, \"mHeight\")"));
    }

    @Test
    public void internalNativeShadowWritesCannotOverwriteVendorBackup() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String ownership = slice(main,
                "private static void installNativeDockShadowOwnership(",
                "/** Re-apply the configured visible shadow");

        int guard = ownership.indexOf("if (nativeShadowInternalCall) return chain.proceed(args);");
        int capture = ownership.indexOf("captureVendorDockShadow(args);");
        assertTrue("internal LiquidDock shadow writes must bypass vendor-state capture",
                guard >= 0 && capture > guard);
        assertTrue("direct native writes must always release their recursion guard",
                main.contains("finally {\n            nativeShadowInternalCall = false;"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
