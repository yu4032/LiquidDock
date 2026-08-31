package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Regression coverage for Launcher -> app widget catalog encoding. */
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

    @Test public void exactMamlRenderDescriptorRoundTripsAnonymousBackground() {
        WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                "maml",
                "b36cd61a-90e9-4d66-ab04-df6957ecff8b",
                "",
                1, 1, 2, 2);
        WidgetComponentStore.Descriptor original = WidgetComponentStore.mamlRenderDescriptor(
                identity,
                "",
                "com.miui.maml.elements.RectangleScreenElement",
                "render/0/2");

        assertNotNull(original);
        assertEquals(WidgetComponentStore.MAML_V2, original.source);
        assertEquals("", original.name);
        assertEquals(WidgetComponentStore.TYPE_BACKGROUND, original.componentType);

        WidgetComponentStore.Descriptor catalog =
                WidgetComponentStore.parseCatalog(original.encodeCatalog());
        assertNotNull(catalog);
        assertEquals(original, catalog);

        WidgetComponentStore.Descriptor selector =
                WidgetComponentStore.parseSelector(original.selectorKey());
        assertNotNull(selector);
        assertEquals(original.source, selector.source);
        assertEquals(original.owner, selector.owner);
        assertEquals(original.name, selector.name);
        assertEquals(original.className, selector.className);
        assertEquals(original.hierarchyPath, selector.hierarchyPath);
    }
}
