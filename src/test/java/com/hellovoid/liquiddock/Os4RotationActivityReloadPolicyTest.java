package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class Os4RotationActivityReloadPolicyTest {
    @Test
    public void rectangularOrientationChangeReloadsOnceAfterSettling() {
        assertTrue(Os4RotationActivityReloadPolicy.shouldReload(
                true, 1, 2, 10_000L, 0L));
        assertFalse(Os4RotationActivityReloadPolicy.shouldReload(
                true, 2, 2, 10_000L, 0L));
        assertFalse(Os4RotationActivityReloadPolicy.shouldReload(
                true, 1, 2, 10_500L, 10_000L));
        assertTrue(Os4RotationActivityReloadPolicy.shouldReload(
                true, 1, 2, 12_000L, 10_000L));
        assertFalse(Os4RotationActivityReloadPolicy.shouldReload(
                false, 1, 2, 12_000L, 0L));
    }
}
