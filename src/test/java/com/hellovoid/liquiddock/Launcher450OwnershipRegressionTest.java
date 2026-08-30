package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contracts derived directly from the HyperOS Launcher 4.50 JADX output. */
public class Launcher450OwnershipRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void prismalDockAndVendorBackgroundNeverOwnStrokeAtTheSameTime() throws Exception {
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("the native foreground owner must have an explicit glass handoff",
                renderer.contains("releaseNativeStrokeOwner"));
        assertTrue("a live Prismal binding must suppress a second native foreground stroke",
                renderer.contains("MiuixGlassHook.isBoundTo(background)"));
        assertTrue("binding Prismal must actively retire any foreground installed before hook order settles",
                glass.contains("DockStrokeRenderer.releaseNativeStrokeOwner(dockBg)"));
        assertTrue("the actual Prismal host remains the configured edge owner",
                glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                host, config.dock, nativeRadius);"));
    }

    @Test
    public void workstationEntryCannotLeaveTheNormalModeNativeStrokeAttached() throws Exception {
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue(renderer.contains("onWorkstationModeChanged(boolean enabled)"));
        assertTrue(renderer.contains("if (!enabled) return;"));
        assertTrue(main.contains("DockStrokeRenderer.onWorkstationModeChanged(enabled);"));
    }

    @Test
    public void remoteViewsBackgroundOwnerMatchesLauncher450RecursiveWidgetFrameLookup()
            throws Exception {
        String suppressor = Files.readString(
                MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue("Launcher 4.50 only enters setBlurIfNeed when the host has one RemoteViews child",
                suppressor.contains("group.getChildCount() != 1"));
        assertTrue("resolve from the same direct RemoteViews content object Launcher 4.50 uses",
                suppressor.contains("View content = group.getChildAt(0);"));
        assertTrue("Launcher 4.50 recursively finds android.R.id.widget_frame inside that content",
                suppressor.contains("content.findViewById(android.R.id.widget_frame)"));
        assertFalse("the old direct keyed-tag heuristic misses nested widget background owners",
                suppressor.contains("child.getTag(android.R.id.widget_frame)"));
    }
}
