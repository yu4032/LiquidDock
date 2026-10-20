package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Geometry behavior is typed-state tested; source checks below are static vendor/target boundaries. */
public class WorkstationDockGeometryContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void workstationGeometryBindsVisibleDockContainer()
            throws Exception {
        String main = source("MainHook.java");
        assertTrue("MainHook must install the visible workstation Dock geometry hook",
                main.contains("WorkstationDockGeometryHook.install(classLoader, config.workstation);"));

        String hook = source("WorkstationDockGeometryHook.java");
        assertTrue(hook.contains("HotSeatsListContentAdapter$LineViewHolder"));
        assertTrue(hook.contains("DockContainer"));
    }
    @Test public void iconRowsNeverReceiveAWorkstationHorizontalTranslation() throws Exception {
        String hook = source("WorkstationDockCustomizationHook.java");
        assertFalse(hook.contains("setTranslationX("));
        assertFalse(hook.contains("applyHorizontal("));
        assertTrue(hook.contains("verticalOffset(recycler)"));
        String schema = source("config/ConfigSchema.java");
        assertFalse(schema.contains("workstation_dock_icon_horizontal_offset"));
        assertFalse(schema.contains("workstation_dock_icon_landscape_horizontal_offset"));
        assertTrue(schema.contains("workstation_dock_icon_top_offset"));
        assertTrue(schema.contains("workstation_dock_icon_landscape_vertical_offset"));
    }
    @Test public void wholeDockTranslationAlsoBindsTheIndependentMaterialShell() throws Exception {
        assertTrue(source("MiuixGlassHook.java").contains(
                "WorkstationDockGeometryHook.bindBackground(materialHost);"));
        String geometry = source("WorkstationDockGeometryHook.java");
        assertTrue(geometry.contains("bindGeometryView(background, false)"));
        assertTrue(geometry.contains("com.miui.home.launcher.hotseats.HotSeats"));
        assertTrue(geometry.contains("HookUtil.tryInvoke(owner, \"getHotSeatsBackground\")"));
        assertTrue(geometry.contains("hasTranslatedAncestor(container)"));
    }
}
