package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure ownership contract for Workstation mode fallback generations and layout snapshots. */
public class WorkstationModeControllerTest {
    @Test
    public void staleFallbackCannotOverrideNewerVendorCallback() {
        WorkstationModeController controller = new WorkstationModeController();
        long pending = controller.beginUnconfirmedProbe();

        controller.onVendorModeChanged(true);

        assertFalse(controller.acceptFallbackProbe(pending, false));
        assertTrue(controller.isWorkstationMode());
    }

    @Test
    public void currentUnconfirmedFallbackIsAcceptedOnce() {
        WorkstationModeController controller = new WorkstationModeController();
        long pending = controller.beginUnconfirmedProbe();

        assertTrue(controller.acceptFallbackProbe(pending, true));
        assertTrue(controller.isWorkstationMode());
        assertFalse(controller.acceptFallbackProbe(pending, false));
    }

    @Test
    public void newProbeInvalidatesOlderProbe() {
        WorkstationModeController controller = new WorkstationModeController();
        long first = controller.beginUnconfirmedProbe();
        long second = controller.beginUnconfirmedProbe();

        assertFalse(controller.acceptFallbackProbe(first, true));
        assertTrue(controller.acceptFallbackProbe(second, false));
    }

    @Test
    public void vendorCallbackAdvancesGenerationAndCancelsFallback() {
        WorkstationModeController controller = new WorkstationModeController();
        long before = controller.generation();
        long pending = controller.beginUnconfirmedProbe();

        controller.onVendorModeChanged(false);

        assertTrue(controller.generation() > before);
        assertFalse(controller.acceptFallbackProbe(pending, true));
        assertFalse(controller.isWorkstationMode());
    }

    @Test
    public void normalLayoutSnapshotRoundTripsExactStoredFields() {
        WorkstationModeController controller = new WorkstationModeController();
        controller.rememberNormalItem(42L, 3L, 4, 5, 2, 1);

        WorkstationModeController.HomeItemPosition item = controller.normalItem(42L);
        assertNotNull(item);
        assertEquals(3L, item.screenId);
        assertEquals(4, item.cellX);
        assertEquals(5, item.cellY);
        assertEquals(2, item.spanX);
        assertEquals(1, item.spanY);
    }

    @Test
    public void clearingNormalLayoutRemovesAllStoredItems() {
        WorkstationModeController controller = new WorkstationModeController();
        controller.rememberNormalItem(42L, 3L, 4, 5, 2, 1);
        controller.clearNormalLayoutBackup();

        assertFalse(controller.hasNormalLayoutBackup());
        assertNull(controller.normalItem(42L));
    }
}
