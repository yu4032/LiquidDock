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
    public void customWholeDockShadowUsesTheActiveVisibleMaterialHost() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String sync = slice(main,
                "static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock)",
                "static void onRuntimeDockShadowDisabled()");

        assertTrue("custom whole-Dock shadow must be applied to the active Dock material",
                sync.contains("applyConfiguredNativeDockShadow(dockBg, dock);"));
        assertFalse("vendor shadow target is not a reliable visible custom-shadow owner",
                sync.contains("View target = nativeShadowTarget();"));
    }

    @Test
    public void customWholeDockShadowOwnsAndReleasesAncestorClipping() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue("native outer shadow must acquire clipping room around the visible Dock",
                main.contains("acquireDockShadowClipOwnership(dockBg);"));
        assertTrue("runtime disable must restore the captured parent clipping state",
                main.contains("releaseDockShadowClipOwnership();"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, Math.max(0, from));
        if (from < 0 || to <= from) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
