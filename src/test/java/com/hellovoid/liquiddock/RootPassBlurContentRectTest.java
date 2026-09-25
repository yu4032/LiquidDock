package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure contract for mapping a root Surface buffer into authoritative content UV space. */
public class RootPassBlurContentRectTest {
    @Test
    public void zeroInsetsUseWholeSurfaceBuffer() {
        RootPassBlurContentRect rect = RootPassBlurContentRect.resolve(3008, 1880, 0, 0, 0, 0);
        assertEquals(0f, rect.left, 0.00001f);
        assertEquals(0f, rect.bottom, 0.00001f);
        assertEquals(1f, rect.width, 0.00001f);
        assertEquals(1f, rect.height, 0.00001f);
        assertTrue(rect.sameAs(RootPassBlurContentRect.full()));
    }

    @Test
    public void asymmetricInsetsMapActualRootContent() {
        RootPassBlurContentRect rect = RootPassBlurContentRect.resolve(3008, 1880, 8, 12, 4, 6);
        assertEquals(8f / 3008f, rect.left, 0.00001f);
        assertEquals(6f / 1880f, rect.bottom, 0.00001f);
        assertEquals((3008f - 8f - 4f) / 3008f, rect.width, 0.00001f);
        assertEquals((1880f - 12f - 6f) / 1880f, rect.height, 0.00001f);
        assertFalse(rect.sameAs(RootPassBlurContentRect.full()));
    }

    @Test
    public void rootSubRectMapsIntoInsetAdjustedBufferUv() {
        RootPassBlurContentRect content =
                RootPassBlurContentRect.resolve(1000, 800, 10, 20, 30, 40);
        RootPassBlurContentRect panel =
                content.subRect(960, 740, 240, 185, 480, 370);

        assertEquals(content.left + 0.25f * content.width, panel.left, 0.00001f);
        assertEquals(content.bottom + 0.25f * content.height, panel.bottom, 0.00001f);
        assertEquals(0.5f * content.width, panel.width, 0.00001f);
        assertEquals(0.5f * content.height, panel.height, 0.00001f);
    }

    @Test
    public void fullRootSubRectPreservesContentRect() {
        RootPassBlurContentRect content =
                RootPassBlurContentRect.resolve(1000, 800, 10, 20, 30, 40);

        assertTrue(content.subRect(960, 740, 0, 0, 960, 740).sameAs(content));
    }

    @Test
    public void invalidGeometryFallsBackToWholeBuffer() {
        assertTrue(RootPassBlurContentRect.resolve(0, 100, 0, 0, 0, 0)
                .sameAs(RootPassBlurContentRect.full()));
        assertTrue(RootPassBlurContentRect.resolve(100, 100, 80, 0, 40, 0)
                .sameAs(RootPassBlurContentRect.full()));
    }
}
