package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalEdgeDecoration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlassEdgeEffectsTest {
    @Test public void allNewMaterialOptionsRemainDisabledByDefault() {
        PrismalEdgeDecoration material = GlassEdgeEffects.resolve(
                new ConfigReader(new HashMap<>()), GlassEdgeEffects.GLOBAL);
        assertFalse(material.hasBacklit());
        assertFalse(material.hasShadow());
    }

    @Test public void dockCanKeepNativeShadowAndUseBacklitOptics() {
        Map<String,Object> map = new HashMap<>();
        map.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        map.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        String dockCast = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        map.put(dockCast, false);
        GlassEdgeEffects.refresh(new ConfigReader(map));
        PrismalEdgeDecoration dock = GlassEdgeEffects.withoutNativeShadow(
                GlassEdgeEffects.DOCK);
        assertTrue(dock.hasBacklit());
        assertFalse(dock.hasShadow());
        assertEquals(1f, dock.inkViewportInsetPx, 0.0001f);
        assertEquals(0f, GlassEdgeEffects.current(GlassEdgeEffects.ICON)
                .inkViewportInsetPx, 0.0001f);
        assertTrue(GlassEdgeEffects.current(GlassEdgeEffects.ICON).hasShadow());
    }

    @Test public void aScopedFalseOnlyOverridesThatField() {
        Map<String,Object> map = new HashMap<>();
        map.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        map.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        map.put(GlassEdgeEffects.key(
                GlassEdgeEffects.FOLDER, ConfigSchema.Glass.BACKLIT_ENABLED), false);
        ConfigReader reader = new ConfigReader(map);
        PrismalEdgeDecoration folder = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.FOLDER);
        PrismalEdgeDecoration widget = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.WIDGET);
        assertFalse(folder.hasBacklit());
        assertTrue(folder.hasShadow());
        assertTrue(widget.hasBacklit());
    }

    @Test public void zeroExtentNeverInsetsDockOrCompilesOptionalRim() {
        Map<String,Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        values.put(ConfigSchema.Glass.BACKLIT_EXTENT.name(), 0);
        GlassEdgeEffects.refresh(new ConfigReader(values));
        PrismalEdgeDecoration dock = GlassEdgeEffects.withoutNativeShadow(GlassEdgeEffects.DOCK);
        assertFalse(dock.hasBacklit());
        assertEquals(0f, dock.inkViewportInsetPx, 0f);
        assertEquals(0f, dock.extraGuardPx(1f), 0f);
    }

    @Test public void scopedExtentInheritsAndCanOverrideSeparately() {
        Map<String,Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        values.put(ConfigSchema.Glass.BACKLIT_EXTENT.name(), 65);
        String dockExtent = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.BACKLIT_EXTENT);
        values.put(dockExtent, 15);
        ConfigReader reader = new ConfigReader(values);
        assertEquals(.15f, GlassEdgeEffects.resolve(
                reader, GlassEdgeEffects.DOCK).backlitExtent, .0001f);
        assertEquals(.65f, GlassEdgeEffects.resolve(
                reader, GlassEdgeEffects.FOLDER).backlitExtent, .0001f);
        assertEquals(.65f, GlassEdgeEffects.resolve(
                reader, GlassEdgeEffects.GLOBAL).backlitExtent, .0001f);
        PrismalEdgeDecoration dock = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.DOCK);
        assertEquals(.60f, dock.backlitSoftness, .0001f);
    }

    @Test public void scopedFractionalDpAndMaterialSoftnessAreIndependent() {
        Map<String,Object> map = new HashMap<>();
        map.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        String w = GlassEdgeEffects.key(
                GlassEdgeEffects.WIDGET, ConfigSchema.Glass.BACKLIT_WIDTH);
        map.put(w, 1);
        map.put(w + "_tenths", 14);
        PrismalEdgeDecoration widget = GlassEdgeEffects.resolve(
                new ConfigReader(map), GlassEdgeEffects.WIDGET);
        assertEquals(1.4f, widget.backlitWidthDp, 0.0001f);
        assertTrue(widget.backlitSoftness > 0f);
        assertEquals(.55f, widget.backlitExtent, 0.0001f);
    }
}
