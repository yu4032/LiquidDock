package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ThirdPartyGlassConfigSchemaTest {
    @Test
    public void searchboxAppearanceKeysAreStableAndOptional() {
        assertEquals("liquid_miui_searchbox_glass", ConfigSchema.Searchbox.ENABLED.name());
        assertEquals("liquid_miui_searchbox_blur", ConfigSchema.Searchbox.BLUR.name());
        assertEquals("liquid_miui_searchbox_tint_r", ConfigSchema.Searchbox.TINT_RED.name());
        assertEquals("liquid_miui_searchbox_tint_g", ConfigSchema.Searchbox.TINT_GREEN.name());
        assertEquals("liquid_miui_searchbox_tint_b", ConfigSchema.Searchbox.TINT_BLUE.name());
        assertEquals("liquid_miui_searchbox_tint_alpha", ConfigSchema.Searchbox.TINT_ALPHA.name());
        assertEquals(ConfigKey.ExportMode.IF_PRESENT, ConfigSchema.Searchbox.BLUR.exportMode());
        assertEquals(ConfigKey.ExportMode.IF_PRESENT, ConfigSchema.Searchbox.TINT_ALPHA.exportMode());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Searchbox.ENABLED));
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Searchbox.BLUR));
    }

    @Test
    public void registeredHiddenProfileFieldsRoundTripOnlyWhenPresent() {
        String scale = ConfigSchema.ThirdPartyGlass.MIUI_SEARCHBOX_CAPTURE_SCALE.name();
        String fps = ConfigSchema.ThirdPartyGlass.MIUI_SEARCHBOX_RENDER_FPS.name();
        String fresh = ConfigSchema.ThirdPartyGlass.MIUI_SEARCHBOX_FRESH_ON_RESUME.name();
        String gboardScale = ConfigSchema.ThirdPartyGlass.GBOARD_CAPTURE_SCALE.name();

        Map<String, Object> empty = new HashMap<>();
        LinkedHashMap<String, Object> absentExport = ConfigCodec.exportValues(empty);
        assertFalse(absentExport.containsKey(scale));
        assertFalse(absentExport.containsKey(fps));
        assertFalse(absentExport.containsKey(fresh));
        assertFalse(absentExport.containsKey(gboardScale));

        Map<String, Object> prefs = new HashMap<>();
        prefs.put(scale, 73);
        prefs.put(fps, 41);
        prefs.put(fresh, false);
        prefs.put(gboardScale, 68);
        LinkedHashMap<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(73, exported.get(scale));
        assertEquals(41, exported.get(fps));
        assertEquals(false, exported.get(fresh));
        assertEquals(68, exported.get(gboardScale));

        LinkedHashMap<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(73, imported.get(scale));
        assertEquals(41, imported.get(fps));
        assertEquals(false, imported.get(fresh));
        assertEquals(68, imported.get(gboardScale));
    }
}