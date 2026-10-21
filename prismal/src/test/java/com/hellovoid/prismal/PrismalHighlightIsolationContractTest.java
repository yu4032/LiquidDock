package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Verifies the profile transformation used when OS4 replaces legacy edge highlights. */
public class PrismalHighlightIsolationContractTest {
    @Test
    public void os4EdgeReplacementDisablesBothLegacyEdgeFamilies() {
        PrismalHighlightProfile configured = new PrismalHighlightProfile(
                true, true, true, true, true, true, true, true, true);

        PrismalHighlightProfile replacement =
                configured.withOs4EdgeReplacingLegacyEdge();

        assertTrue(replacement.skyHaze);
        assertFalse(replacement.specular);
        assertFalse(replacement.litRim);
        assertFalse(replacement.oppositeRim);
        assertFalse(replacement.cornerRim);
        assertFalse(replacement.faceSheen);
        assertFalse(replacement.plainHighlight);
        assertTrue(replacement.caustics);
        assertTrue(replacement.pressGlow);
        assertTrue(replacement.os4Edge);
    }
}
