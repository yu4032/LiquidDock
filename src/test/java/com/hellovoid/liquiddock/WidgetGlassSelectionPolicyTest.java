package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class WidgetGlassSelectionPolicyTest {
    @Test
    public void emptySelectionLeavesWidgetGlassDisabledByDefault() {
        assertFalse(WidgetGlassSelectionPolicy.isEnabled(Set.of(), "R2\tcom.example/.Clock"));
        assertFalse(WidgetGlassSelectionPolicy.isEnabled(Set.of(), "M\tweather_product"));
    }

    @Test
    public void selectionEnablesOnlyExactWidgetType() {
        Set<String> selected = Set.of("R2\tcom.example/.Clock");

        assertTrue(WidgetGlassSelectionPolicy.isEnabled(selected, "R2\tcom.example/.Clock"));
        assertFalse(WidgetGlassSelectionPolicy.isEnabled(selected, "R2\tcom.example/.Weather"));
    }

    @Test
    public void groupKeyUsesCatalogWidgetTypeIdentity() {
        WidgetComponentStore.Descriptor remote = WidgetComponentStore.remoteDescriptor(
                "com.example/.Clock", WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                "background", "android.view.View", "0", WidgetComponentStore.TYPE_BACKGROUND);
        WidgetBackgroundIdentity mamlIdentity = new WidgetBackgroundIdentity(
                "maml", "weather_product", "com.example.weather", 4, 2, 4, 2);
        WidgetComponentStore.Descriptor maml = WidgetComponentStore.mamlDescriptor(
                mamlIdentity, "background", "com.miui.maml.elements.RectangleScreenElement");

        assertTrue(remote != null);
        assertTrue(maml != null);
        assertTrue("R2\tcom.example/.Clock".equals(WidgetGlassSelectionPolicy.groupKey(remote)));
        assertTrue("M\tweather_product".equals(WidgetGlassSelectionPolicy.groupKey(maml)));
    }
}
