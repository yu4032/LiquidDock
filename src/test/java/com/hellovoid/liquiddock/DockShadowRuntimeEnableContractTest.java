package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Runtime contracts for turning both Dock shadow features back on without restarting Launcher. */
public class DockShadowRuntimeEnableContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void wholeDockShadowFalseToTrueRefreshesHotSeatsNativeLifecycle() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String enable = slice(main,
                "static void onRuntimeDockShadowEnabled()",
                "static void onRuntimeDockCustomizationDisabled()");

        assertTrue("false->true dock_shadow must dispatch an immediate reapply",
                state.contains("!wasDockShadowEnabled && nextLiveDockShadowEnabled")
                        && state.contains("MainHook.onRuntimeDockShadowEnabled()"));
        assertTrue("whole-Dock runtime enable must refresh HotSeats' existing native owner",
                enable.contains("refreshVendorDockShadow();"));
        assertTrue("runtime refresh must delegate to HotSeats.showViewShadow",
                main.contains("HookUtil.invoke(hotSeats, \"showViewShadow\")"));
    }

    @Test
    public void strokeShadowTransitionsRefreshInstalledStrokeDrawable() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));

        assertTrue("stroke_shadow transitions must refresh the installed foreground style",
                state.contains("wasStrokeShadowEnabled != nextLiveStrokeShadowEnabled")
                        && state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
        assertTrue("the same transition must refresh the final native-shadow bridge",
                state.contains("DockNativeShadowBridge.refreshConfig()")
                        && state.contains("MainHook.onRuntimeDockShadowEnabled()"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) return "";
        return source.substring(from, to);
    }
}
