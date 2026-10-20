package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Protects the cross-window and rotation timing boundaries of the Workstation taskbar. */
public class WorkstationTaskbarReconciliationContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void allowsIconRowsInDifferentDockWindowRoots() throws Exception {
        String output = source("Miuix307ZeroCopyRenderer.java");
        assertFalse("cross-window icon rows must not be filtered by ViewRoot identity",
                output.contains("material.getRootView() != recycler.getRootView()"));
        assertTrue("the icon and material need to share the display coordinate system",
                output.contains("materialDisplay.getDisplayId() != rowDisplay.getDisplayId()"));
    }

    @Test public void reconcilesContainerAndIconsBeforeGpuMapping() throws Exception {
        String output = source("Miuix307PassBlurTextureView.java");
        int placement = output.indexOf(
                "WorkstationDockGeometryHook.reconcileTaskbarPlacementForFrame()");
        int icons = output.indexOf(
                "WorkstationDockCustomizationHook.reconcileTaskbarRowsForFrame()");
        int backdrop = output.indexOf("updateBackdropMapping();", icons);
        assertTrue("pre-draw must first reconcile the Dock's native bottom position",
                placement >= 0);
        assertTrue("icon centering must follow Dock placement", icons > placement);
        assertTrue("GPU backdrop mapping must see the reconciled geometry", backdrop > icons);
    }

    @Test public void restoresNativeIconLayoutAndAvoidsOneShotOnlyCentering() throws Exception {
        String rows = source("WorkstationDockCustomizationHook.java");
        String dock = source("WorkstationDockGeometryHook.java");
        assertTrue(rows.contains("onWorkstationModeChanged(boolean active)"));
        assertTrue(rows.contains("row.restore(recycler)"));
        assertTrue(dock.contains("reconcileTaskbarPosition()"));
        assertFalse("a one-shot pending callback cannot be the sole orientation correction",
                rows.contains("row.pending"));
    }
}
