package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Typed contract derived from HyperOS libgui/libsurfaceflinger PassBlur state machine. */
public class PassBlurForceRefreshContractTest {
    @Test
    public void dockGetsSixtySecondForceRefreshWindow() {
        assertEquals(60_000, PassBlurForceRefreshPolicy.timeoutMs(PassBlurDomain.DOCK));
    }

    @Test
    public void otherDomainsAreNotForceRefreshedByThisProbe() {
        assertEquals(-1, PassBlurForceRefreshPolicy.timeoutMs(PassBlurDomain.LAUNCHER_WORKSPACE));
        assertEquals(-1, PassBlurForceRefreshPolicy.timeoutMs(PassBlurDomain.SECURITY_CENTER));
    }
}
