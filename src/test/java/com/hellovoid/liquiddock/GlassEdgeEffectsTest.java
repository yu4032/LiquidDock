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

    @Test public void newOpticalDefaultsAreConsistentAcrossScopes() {
        assertEquals(.4f, ConfigSchema.Glass.BACKLIT_WIDTH.uiDefaultDp(), .0001f);
        assertEquals(.4f, ConfigSchema.Glass.BACKLIT_WIDTH.runtimeFallbackDp(), .0001f);
        assertEquals(Integer.valueOf(20), ConfigSchema.Glass.BACKLIT_STRENGTH.uiDefault());
        assertEquals(Integer.valueOf(100), ConfigSchema.Glass.BACKLIT_EXTENT.uiDefault());
        assertEquals(Integer.valueOf(68), ConfigSchema.Glass.BACKLIT_SOFTNESS.uiDefault());
        assertEquals(Integer.valueOf(5), ConfigSchema.Glass.OUTER_SHADOW_RADIUS.uiDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y.uiDefault());
        assertEquals(Integer.valueOf(65), ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW.uiDefault());
        assertEquals(Integer.valueOf(8), ConfigSchema.Glass.OUTER_SHADOW_ALPHA.uiDefault());
        Map<String,Object> opts = new HashMap<>();
        opts.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        opts.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        ConfigReader blank = new ConfigReader(opts);
        for (String scope : new String[] {GlassEdgeEffects.GLOBAL,
                GlassEdgeEffects.DOCK, GlassEdgeEffects.WIDGET,
                GlassEdgeEffects.FOLDER}) {
            PrismalEdgeDecoration d = GlassEdgeEffects.resolve(blank,scope);
            assertEquals(.4f,d.backlitWidthDp,.0001f);
            assertEquals(.20f,d.backlitStrength,.0001f);
            assertEquals(1f,d.backlitExtent,.0001f);
            assertEquals(.68f,d.backlitSoftness,.0001f);
            assertEquals(5f,d.shadowRadiusDp,.0001f);
            assertEquals(0f,d.shadowOffsetYDp,.0001f);
            assertEquals(.65f,d.shadowLightFollow,.0001f);
            assertEquals(8f/255f,d.shadowA,.0001f);
        }
    }

    @Test public void savedFractionalWidthsOverrideNewDefault() {
        Map<String,Object> map=new HashMap<>();
        map.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(),true);
        map.put(ConfigSchema.Glass.BACKLIT_WIDTH.name(),1);
        map.put(ConfigSchema.Glass.BACKLIT_WIDTH.name()+"_tenths",12);
        ConfigReader existing=new ConfigReader(map);
        assertEquals(1.2f,GlassEdgeEffects.resolve(
                existing,GlassEdgeEffects.WIDGET).backlitWidthDp,.0001f);
        String dockWidth=GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK,ConfigSchema.Glass.BACKLIT_WIDTH);
        map.put(dockWidth,0);
        map.put(dockWidth+"_tenths",6);
        ConfigReader scoped=new ConfigReader(map);
        assertEquals(.6f,GlassEdgeEffects.resolve(
                scoped,GlassEdgeEffects.DOCK).backlitWidthDp,.0001f);
        assertEquals(1.2f,GlassEdgeEffects.resolve(
                scoped,GlassEdgeEffects.FOLDER).backlitWidthDp,.0001f);
    }

    @Test public void dockUsesSamePrismalShadowMaterialAsWidgetAndIcon() {
        Map<String,Object> map = new HashMap<>();
        map.put(ConfigSchema.Glass.BACKLIT_ENABLED.name(), true);
        map.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        String dockCast = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        map.put(dockCast, true);
        GlassEdgeEffects.refresh(new ConfigReader(map));
        PrismalEdgeDecoration dock = GlassEdgeEffects.current(
                GlassEdgeEffects.DOCK);
        assertTrue(dock.hasBacklit());
        assertTrue(dock.hasShadow());
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
        PrismalEdgeDecoration dock = GlassEdgeEffects.current(GlassEdgeEffects.DOCK);
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
        assertEquals(.68f, dock.backlitSoftness, .0001f);
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
        assertEquals(1f, widget.backlitExtent, 0.0001f);
    }
}
