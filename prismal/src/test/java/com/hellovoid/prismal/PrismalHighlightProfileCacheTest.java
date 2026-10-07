package com.hellovoid.prismal;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalHighlightProfileCacheTest {
    @Test
    public void os4ReplacementIsCachedPerImmutableProfile() {
        PrismalHighlightProfile source = new PrismalHighlightProfile(
                true, true, true, true, true, true, true, true, true);

        PrismalHighlightProfile first = source.withOs4EdgeReplacingLegacyEdge();
        PrismalHighlightProfile second = source.withOs4EdgeReplacingLegacyEdge();

        assertSame(first, second);
        assertTrue(first.os4Edge);
        assertSame(first, first.withOs4EdgeReplacingLegacyEdge());
    }
}
