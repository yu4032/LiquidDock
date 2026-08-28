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
    public void wholeDockShadowUsesLauncherHotSeatsAsTheOnlyNativeLifecycleOwner() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String ownership = slice(main,
                "private static void installNativeDockShadowOwnership(",
                "private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(");

        assertTrue("whole-Dock shadow must keep HotSeats.showViewShadow as the renderer",
                ownership.contains("getDeclaredMethod(\"showViewShadow\")"));
        assertTrue("HotSeats owner must be retained only to request vendor redraws",
                main.contains("hotSeatsShadowOwnerRef"));
        assertFalse("the visible Dock material must not become a second native shadow owner",
                main.contains("applyConfiguredNativeDockShadow(dockBg"));
        assertFalse("terminal native target ownership must not remain",
                main.contains("nativeShadowTargetRef"));
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
