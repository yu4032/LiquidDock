package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contract: whole-Dock shadow must reuse Launcher's real native owner and call path. */
public class DockVendorNativeShadowReuseContractTest {
    private static String mainHook() throws Exception {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void liquidDockDoesNotCreateASecondNativeShadowOwner() throws Exception {
        String main = mainHook();
        assertFalse("the visible Dock background must not be treated as a second native shadow owner",
                main.contains("customShadowTargetRef"));
        assertFalse("whole-Dock native shadow must not acquire custom ancestor clipping ownership",
                main.contains("acquireDockShadowClipOwnership"));
        assertFalse("whole-Dock native shadow must not be manually applied to dockBg",
                main.contains("applyConfiguredNativeDockShadow(dockBg, dock)"));
    }

    @Test
    public void vendorApplyViewShadowCallKeepsVendorTargetAndOnlyRewritesParameters() throws Exception {
        String main = mainHook();
        String ownership = slice(main,
                "private static void installNativeDockShadowOwnership(",
                "/** Re-apply the configured native shadow");

        assertTrue("the vendor shadow target must remain the target of configured native shadow args",
                ownership.contains("configuredNativeDockShadowArgs(\n                                    vendorTarget"));
        assertTrue("dock_shadow=false must clear that same vendor target",
                ownership.contains("clearNativeDockShadowArgs(vendorTarget)"));
        assertFalse("the vendor call must not depend on a second custom shadow target",
                ownership.contains("customShadowTarget()"));
    }

    @Test
    public void runtimeRefreshUsesTheAuthoritativeVendorTarget() throws Exception {
        String main = mainHook();
        String sync = slice(main,
                "static void syncDockShadow(",
                "static void onRuntimeDockShadowDisabled()");

        assertTrue("runtime refresh must resolve the authoritative native shadow target",
                sync.contains("View target = nativeShadowTarget();"));
        assertTrue("enabled shadow must refresh the authoritative native target",
                sync.contains("applyConfiguredNativeDockShadow(target, dock);"));
        assertTrue("disabled shadow must clear the authoritative native target",
                sync.contains("clearNativeDockShadowArgs(target)"));
        assertFalse("runtime refresh must not suppress the real vendor target first",
                sync.contains("suppressVendorDockShadow()"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) return "";
        return source.substring(from, to);
    }
}
