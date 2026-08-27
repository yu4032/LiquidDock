package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Runtime contracts for turning both Dock shadow features back on without restarting Launcher. */
public class DockShadowRuntimeEnableContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void wholeDockShadowFalseToTrueReappliesAuthoritativeNativeOwner() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue("false->true dock_shadow must dispatch an immediate reapply",
                state.contains("!wasDockShadowEnabled && nextLiveDockShadowEnabled")
                        && state.contains("MainHook.onRuntimeDockShadowEnabled()"));
        assertTrue("whole-Dock runtime enable must resync the already-known native target",
                main.contains("static void onRuntimeDockShadowEnabled()")
                        && main.contains("syncDockShadow(oldBg(), LiquidDockConfig.load().dock);"));
        assertTrue("sync must use the authoritative Launcher native owner",
                main.contains("View target = nativeShadowTarget();"));
    }

    @Test
    public void strokeShadowFalseToTrueRefreshesInstalledStrokeDrawable() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));

        assertTrue("false->true stroke_shadow must refresh the installed foreground style",
                state.contains("!wasStrokeShadowEnabled && nextLiveStrokeShadowEnabled")
                        && state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }
}
