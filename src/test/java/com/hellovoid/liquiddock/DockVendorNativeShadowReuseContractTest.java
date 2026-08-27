package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract derived from the decompiled HyperOS 3 HotSeats shadow lifecycle. */
public class DockVendorNativeShadowReuseContractTest {
    private static String mainHook() throws Exception {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void wholeDockShadowHooksHotSeatsLifecycleInsteadOfTerminalMiShadowApi() throws Exception {
        String main = mainHook();
        assertTrue("stock shadow authority is HotSeats.showViewShadow",
                main.contains("\"showViewShadow\""));
        assertTrue("stock animated shadow authority is HotSeats.setTranslationY",
                main.contains("\"setTranslationY\""));
        assertFalse("LiquidDock must not replace the terminal MiShadowUtils.applyViewShadow lifecycle",
                main.contains("HookUtil.hookMethod(ms, \"applyViewShadow\""));
    }

    @Test
    public void configuredShadowTemporarilyUsesVendorHotSeatsState() throws Exception {
        String main = mainHook();
        assertTrue("custom radius must use HotSeats' native radius field",
                main.contains("mMiShadowRadius"));
        assertTrue("custom Y offset must use HotSeats' native offset field",
                main.contains("mMiShadowOffsetY"));
        assertTrue("custom alpha must preserve the vendor MI_SHADOW_ALPHA calculation path",
                main.contains("MI_SHADOW_ALPHA"));
    }

    @Test
    public void runtimeRefreshDelegatesBackToVendorShowViewShadow() throws Exception {
        String main = mainHook();
        assertTrue("runtime shadow changes must ask HotSeats to redraw its own shadow",
                main.contains("HookUtil.invoke(hotSeats, \"showViewShadow\")"));
        assertFalse("runtime refresh must not manually replay MiShadow on a cached target",
                main.contains("applyConfiguredNativeDockShadow("));
        assertFalse("runtime refresh must not keep a second native shadow target authority",
                main.contains("nativeShadowTargetRef"));
    }

    @Test
    public void geometryCallbacksOnlyTrackConfigAndNeverReplayWholeDockShadow() throws Exception {
        String main = mainHook();
        String sync = slice(main,
                "static void syncDockShadow(",
                "static void onRuntimeDockShadowDisabled()");
        assertTrue("geometry sync may keep the current background/config reference",
                sync.contains("setOldBg(dockBg)"));
        assertFalse("vendor updateRoundRect owns native shadow geometry; sync must stay shadow-passive",
                sync.contains("refreshVendorDockShadow()"));
        assertFalse("geometry sync must never invoke MiShadow directly",
                sync.contains("applyViewShadow"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) return "";
        return source.substring(from, to);
    }
}
