package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract derived from the decompiled HyperOS 3 / Launcher 4.50 HotSeats lifecycle. */
public class DockVendorNativeShadowReuseContractTest {
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
    public void wholeDockShadowKeepsHotSeatsAsLifecycleOwner() throws Exception {
        String main = mainHook();
        String bridge = shadowBridge();
        assertTrue("stock shadow authority is HotSeats.showViewShadow",
                main.contains("\"showViewShadow\""));
        assertTrue("stock animated shadow authority is HotSeats.setTranslationY",
                main.contains("\"setTranslationY\""));
        assertTrue("terminal API bridge must only rewrite arguments and proceed vendor rendering",
                bridge.contains("HookUtil.hookMethod(utils, \"applyViewShadow\"")
                        && bridge.contains("rewrite(args);")
                        && bridge.contains("return chain.proceed(args);"));
        assertFalse("MainHook itself must not replace terminal MiShadow lifecycle",
                main.contains("HookUtil.hookMethod(ms, \"applyViewShadow\""));
    }

    @Test
    public void configuredShadowUsesVendorHotSeatsGeometryButTerminalAlpha() throws Exception {
        String main = mainHook();
        String bridge = shadowBridge();
        assertTrue("custom radius must use HotSeats' native radius field",
                main.contains("mMiShadowRadius"));
        assertTrue("custom Y offset must use HotSeats' native offset field",
                main.contains("mMiShadowOffsetY"));
        assertFalse("Launcher 4.50 HotSeats alpha must never be used as a shadow control",
                main.contains("MI_SHADOW_ALPHA") || main.contains("overrideViewAlpha(")
                        || main.contains("mMiShadowAlpha") || main.contains("mShadowAlpha"));
        assertTrue("custom shadow alpha must be rewritten in MiShadow arguments",
                bridge.contains("int dockAlpha = dockShadow ? clamp255(dock.shadowAlpha) : 0;")
                        && bridge.contains("args[1] = Color.argb(outAlpha,"));
    }

    @Test
    public void runtimeRefreshDelegatesBackToVendorShowViewShadow() throws Exception {
        String main = mainHook();
        assertTrue("runtime shadow changes must ask HotSeats to redraw its own shadow",
                main.contains("HookUtil.InvocationResult<Object> refresh = HookUtil.tryInvoke(hotSeats, \"showViewShadow\")")
                        && main.contains("if (!refresh.succeeded())"));
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
