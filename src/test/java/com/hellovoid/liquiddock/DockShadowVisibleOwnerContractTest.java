package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts derived from the device regression where both Dock shadow modes became invisible. */
public class DockShadowVisibleOwnerContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void zeroCopyStrokeAndStrokeShadowLiveOnGlassHost() throws Exception {
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("the zero-copy stroke drawable must be installed on the visible glass host",
                glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                host, config.dock, nativeRadius);"));
        assertFalse("the transparent vendor material must not remain the zero-copy stroke owner",
                glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                dockBg, config.dock, nativeRadius);"));
    }

    @Test
    public void wholeDockShadowUsesLauncherAuthoritativeNativeTarget() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String sync = slice(main,
                "static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock)",
                "static void onRuntimeDockShadowDisabled()");

        assertTrue("whole-Dock shadow refresh must target Launcher's authoritative native owner",
                sync.contains("View target = nativeShadowTarget();"));
        assertTrue("configured shadow must be applied to that native target",
                sync.contains("applyConfiguredNativeDockShadow(target, dock);"));
        assertFalse("the visible Dock material must not become a second native shadow owner",
                sync.contains("applyConfiguredNativeDockShadow(dockBg, dock);"));
    }

    @Test
    public void nativeWholeDockShadowDoesNotTakeCustomAncestorClippingOwnership() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertFalse("Launcher already owns clipping/layer placement for its native shadow target",
                main.contains("acquireDockShadowClipOwnership"));
        assertFalse("LiquidDock must not retain a second shadow clipping lifecycle",
                main.contains("releaseDockShadowClipOwnership"));
        assertFalse("LiquidDock must not retain a second custom native shadow target",
                main.contains("customShadowTargetRef"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
