package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceGeometrySwapPacingPolicyTest {
    @Test public void coalescesOnlyInsideRefreshInterval() {
        long period = WorkspaceGeometrySwapPacingPolicy.framePeriodNs(165f);
        assertEquals(6_060_606L, period);
        long t0 = 100_000_000L;
        assertEquals(6L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                t0, t0 + 1_000_000L, period));
        assertEquals(1L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                t0, t0 + 6_000_000L, period));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                t0, t0 + period, period));
    }

    @Test public void neverDelaysInitialSurfaceOrClockReset() {
        long period = WorkspaceGeometrySwapPacingPolicy.framePeriodNs(165f);
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                0L, 100_000_000L, period));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                200_000_000L, 100_000_000L, period));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                100_000_000L, 100_000_000L, period));
    }

    @Test public void rejectsUnknownDisplayRateInsteadOfGuessing() {
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.framePeriodNs(0f));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.framePeriodNs(Float.NaN));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.framePeriodNs(300f));
        assertEquals(0L, WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                100_000_000L, 101_000_000L, 0L));
    }

    @Test public void fallbackDelayIsBoundedToOneRefreshInterval() {
        for (float hz : new float[]{60f, 90f, 120f, 144f, 165f}) {
            long period = WorkspaceGeometrySwapPacingPolicy.framePeriodNs(hz);
            long delayMs = WorkspaceGeometrySwapPacingPolicy.geometryDelayMs(
                    100_000_000L, 100_000_001L, period);
            assertTrue(delayMs > 0L);
            assertTrue(delayMs <= Math.ceil(period / 1_000_000.0));
        }
    }
}
