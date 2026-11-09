package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetProportionalScalePolicyTest {
    @Test public void onlyModuleExpandedWidgetsCanShrink() {
        assertEquals(2, WidgetProportionalScalePolicy.toggleSpan(1, 1, false));
        assertEquals(1, WidgetProportionalScalePolicy.toggleSpan(2, 2, true));
        assertEquals(0, WidgetProportionalScalePolicy.toggleSpan(2, 2, false));
        assertEquals(0, WidgetProportionalScalePolicy.toggleSpan(1, 2, true));
    }

    @Test public void oneByOneOnly() {
        assertTrue(WidgetProportionalScalePolicy.eligible(1, 1));
        assertFalse(WidgetProportionalScalePolicy.eligible(0, 1));
        assertFalse(WidgetProportionalScalePolicy.eligible(1, 2));
        assertFalse(WidgetProportionalScalePolicy.eligible(2, 2));
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

    @Test public void enlargedMamlUsesOneCellLogicalViewportBeforeDrawing() {
        WidgetProportionalScalePolicy.ContentViewport viewport =
                WidgetProportionalScalePolicy.contentViewport(646, 646, 286, 286, 0.7f);
        assertNotNull(viewport);
        // The provider uses view_width to position its elements; a 90% anchor
        // must still be at 90% of the enlarged host after the canvas transform.
        double providerX = viewport.logicalWidth * 0.9;
        assertEquals(646 * 0.9, viewport.left + providerX * 0.7f * viewport.scale, 0.001);
        assertEquals(286 / 0.7, viewport.logicalHeight, 0.001);
    }

    @Test public void rectangularHostCentersTheWholeSourceViewport() {
        WidgetProportionalScalePolicy.ContentViewport viewport =
                WidgetProportionalScalePolicy.contentViewport(640, 480, 280, 280, 1f);
        assertNotNull(viewport);
        assertEquals(80f, viewport.left, 0.001f);
        assertEquals(0f, viewport.top, 0.001f);
        assertEquals(560f, viewport.left + 280 * viewport.scale, 0.001f);
        assertNull(WidgetProportionalScalePolicy.contentViewport(640, 480, 280, 280, 0f));
    }
}
