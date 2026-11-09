package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetProportionalScalePolicyTest {
    @Test public void fixedOneByOneOnly() {
        assertTrue(WidgetProportionalScalePolicy.eligible(1, 1, 0));
        assertFalse(WidgetProportionalScalePolicy.eligible(1, 1, 3));
        assertFalse(WidgetProportionalScalePolicy.eligible(1, 2, 0));
        assertFalse(WidgetProportionalScalePolicy.eligible(2, 2, 0));
    }

    @Test public void uniformScalePreservesSourceAspectRatio() {
        WidgetProportionalScalePolicy.Frame f =
                WidgetProportionalScalePolicy.fit(4, 10, 220, 190, 100, 90);
        assertNotNull(f);
        assertEquals(100, f.width);
        assertEquals(90, f.height);
        assertEquals(190f / 90f, f.scale, 0.0001f);
        assertEquals(64, f.left);
        assertEquals(60, f.top);
    }

    @Test public void invalidOrTooSmallTargetDoesNotScale() {
        assertNull(WidgetProportionalScalePolicy.fit(0, 0, 0, 200, 100, 100));
        assertNotNull(WidgetProportionalScalePolicy.fit(0, 0, 80, 80, 100, 100));
    }

    @Test public void letterboxKeepsCenter() {
        WidgetProportionalScalePolicy.Frame f =
                WidgetProportionalScalePolicy.fit(0, 0, 250, 160, 100, 100);
        assertEquals(75, f.left);
        assertEquals(30, f.top);
        assertEquals(1.6f, f.scale, 0f);
    }
}
