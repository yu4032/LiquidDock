package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HandleMenuSurfaceCornerLeaseTest {
    @Test public void nativeRadiusMatchesObservedWindowModes() {
        assertEquals(74.5f, HandleMenuSurfaceCornerLease.nativeRadius(147, false), 0);
        assertEquals(74.5f, HandleMenuSurfaceCornerLease.nativeRadius(294, true), 0);
    }

    @Test public void customRadiusReachesCompositorAndAutoRestoresExactNativeValue() {
        List<Float> writes = new ArrayList<>();
        HandleMenuSurfaceCornerLease lease = new HandleMenuSurfaceCornerLease(
                74.5f, radius -> { writes.add(radius); return true; });
        assertTrue(lease.update(-1));
        assertTrue(writes.isEmpty());
        assertTrue(lease.update(0));
        assertTrue(lease.update(24));
        assertTrue(lease.update(-1));
        assertEquals(List.of(0f, 24f, 74.5f), writes);
    }

    @Test public void fallbackOrReleaseRestoresOnceAndRepeatedValuesDoNotResubmit() {
        List<Float> writes = new ArrayList<>();
        HandleMenuSurfaceCornerLease lease = new HandleMenuSurfaceCornerLease(
                74.5f, radius -> { writes.add(radius); return true; });
        lease.update(12);
        lease.update(12);
        lease.restore();
        lease.restore();
        assertEquals(List.of(12f, 74.5f), writes);
    }

    @Test public void failedTransactionDoesNotClaimAnOverride() {
        List<Float> attempts = new ArrayList<>();
        HandleMenuSurfaceCornerLease lease = new HandleMenuSurfaceCornerLease(
                74.5f, radius -> { attempts.add(radius); return false; });
        assertFalse(lease.update(12));
        assertTrue(lease.restore());
        assertEquals(List.of(12f), attempts);
    }
}
