package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Regression coverage for Launcher -> app RemoteViews catalog encoding. */
public class WidgetComponentStoreRoundTripTest {
    @Test public void remoteDescriptorRoundTripsWithEmptyDisplayLabel() {
        WidgetComponentStore.Descriptor original = WidgetComponentStore.remoteDescriptor(
                "com.android.calendar/.widget.MonthWidgetProviderNew",
                WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                "widget_frame",
                "com.miui.miuiwidget.views.MIUIWidgetFrameLayout",
                "0/0",
                WidgetComponentStore.TYPE_BACKGROUND);

        assertNotNull(original);
        assertEquals("", original.label);

        WidgetComponentStore.Descriptor parsed =
                WidgetComponentStore.parseCatalog(original.encodeCatalog());
        assertNotNull(parsed);
        assertEquals(original, parsed);
    }
}
