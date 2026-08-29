package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class DockIconAnimationRenderingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void zeroCopyRendererInstallsDockAnimationHookOnLauncherClassLoader() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String install = slice(renderer,
                "static boolean install(ViewGroup materialHost",
                "static boolean isInstalled()");

        assertTrue(install.contains("DockIconAnimationGlassHook.install("));
        assertTrue(install.contains("materialHost.getClass().getClassLoader()"));
    }

    @Test
    public void closeToHomeUsesFrameCommitHandoffWithoutProgressPreroll() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String proxyHook = slice(hook,
                "private static boolean installFloatingProxyHook",
                "private static boolean beginFrameCommitHandoff");
        String handoff = slice(hook,
                "private static boolean beginFrameCommitHandoff",
                "private static void rememberCloseToHomeTarget");

        assertTrue(proxyHook.contains("((Number) args[3]).floatValue()"));
        assertTrue(proxyHook.contains("proxy.setAlpha(1.0f)"));
        assertTrue(hook.contains("installFloatingViewFinishHandoffHook"));
        assertTrue(handoff.contains("registerFrameCommitCallback"));
        assertTrue(handoff.contains("setAnimTargetVisibility"));
        assertTrue(handoff.contains("completeFrameCommitHandoff"));
        assertFalse(hook.contains("primeNativeSourceForHandoff"));
        assertFalse(hook.contains("TAIL_SOURCE_OWNERS"));
        assertFalse(hook.contains("FINAL_PROGRESS"));
    }

    @Test
    public void nativeHandoffIsIndependentFromIconGlass() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String handoff = slice(hook,
                "private static boolean beginFrameCommitHandoff",
                "private static void rememberCloseToHomeTarget");
        String proxyHook = slice(hook,
                "private static boolean installFloatingProxyHook",
                "private static boolean beginFrameCommitHandoff");

        assertFalse(handoff.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(proxyHook.contains("if (GlassRuntimeState.isIconEnabled())"));
        assertTrue(proxyHook.contains("DockGlassItemRegistry.observeLaunchAnimationFrame("));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
