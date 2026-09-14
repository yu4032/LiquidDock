package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DockPassBlurRenderPlanTest {
    @Test public void workspaceScaleChangesPhysicalBackdropButKeepsLogicalOutput() {
        DockPassBlurRenderPlan plan = DockPassBlurRenderPlan.resolve(2400, 1080, 75);

        assertEquals(2400, plan.logicalWidth);
        assertEquals(1080, plan.logicalHeight);
        assertEquals(1800, plan.physicalWidth);
        assertEquals(810, plan.physicalHeight);
        assertEquals(2400, plan.outputWidth);
        assertEquals(1080, plan.outputHeight);
    }

    @Test public void fullAndHalfScalePreserveLogicalOutputExtent() {
        DockPassBlurRenderPlan full = DockPassBlurRenderPlan.resolve(2000, 1000, 100);
        DockPassBlurRenderPlan half = DockPassBlurRenderPlan.resolve(2000, 1000, 50);

        assertEquals(2000, full.physicalWidth);
        assertEquals(1000, full.physicalHeight);
        assertEquals(1000, half.physicalWidth);
        assertEquals(500, half.physicalHeight);
        assertEquals(full.outputWidth, half.outputWidth);
        assertEquals(full.outputHeight, half.outputHeight);
    }
}
