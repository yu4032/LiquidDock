package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ThirdPartyGlassConfigCodecTest {
    @Test
    public void exportAndImportPreserveOnlyKnownProfileFields() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("third_party_glass.miui.searchbox.enabled", true);
        prefs.put("third_party_glass.miui.searchbox.blur", 22.5f);
        prefs.put("third_party_glass.miui.searchbox.tint_alpha", 120);
        prefs.put("third_party_glass.miui.searchbox.capture_scale_percent", 72);
        prefs.put("third_party_glass.miui.searchbox.render_fps", 45);
        prefs.put("third_party_glass.miui.searchbox.highlight_specular", false);
        prefs.put("third_party_glass.future.adapter.corner_radius_dp", 18.5f);
        prefs.put("third_party_glass.future.adapter.hook_class", "bad.DynamicHook");
        prefs.put("other.dynamic.key", true);

        LinkedHashMap<String, Object> exported = ConfigCodec.exportValues(prefs);

        assertEquals(true, exported.get("third_party_glass.miui.searchbox.enabled"));
        assertEquals(22.5f, ((Number) exported.get("third_party_glass.miui.searchbox.blur")).floatValue(), 0.001f);
        assertEquals(120, exported.get("third_party_glass.miui.searchbox.tint_alpha"));
        assertEquals(72, exported.get("third_party_glass.miui.searchbox.capture_scale_percent"));
        assertEquals(45, exported.get("third_party_glass.miui.searchbox.render_fps"));
        assertEquals(false, exported.get("third_party_glass.miui.searchbox.highlight_specular"));
        assertEquals(18.5f, ((Number) exported.get("third_party_glass.future.adapter.corner_radius_dp")).floatValue(), 0.001f);
        assertFalse(exported.containsKey("third_party_glass.future.adapter.hook_class"));
        assertFalse(exported.containsKey("other.dynamic.key"));

        LinkedHashMap<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(true, imported.get("third_party_glass.miui.searchbox.enabled"));
        assertEquals(22.5f, ((Number) imported.get("third_party_glass.miui.searchbox.blur")).floatValue(), 0.001f);
        assertEquals(120, imported.get("third_party_glass.miui.searchbox.tint_alpha"));
        assertEquals(72, imported.get("third_party_glass.miui.searchbox.capture_scale_percent"));
        assertEquals(45, imported.get("third_party_glass.miui.searchbox.render_fps"));
        assertEquals(false, imported.get("third_party_glass.miui.searchbox.highlight_specular"));
    }

    @Test
    public void importClampsProfileNumbersAndRejectsInvalidProfileId() {
        Map<String, Object> json = new HashMap<>();
        json.put("third_party_glass.miui.searchbox.tint_r", 999);
        json.put("third_party_glass.miui.searchbox.capture_scale_percent", 1);
        json.put("third_party_glass.miui.searchbox.render_fps", 999);
        json.put("third_party_glass.bad/profile.enabled", true);

        LinkedHashMap<String, Object> imported = ConfigCodec.importValues(json);

        assertEquals(255, imported.get("third_party_glass.miui.searchbox.tint_r"));
        assertEquals(50, imported.get("third_party_glass.miui.searchbox.capture_scale_percent"));
        assertEquals(60, imported.get("third_party_glass.miui.searchbox.render_fps"));
        assertFalse(imported.containsKey("third_party_glass.bad/profile.enabled"));
        assertTrue(imported.containsKey(ConfigSchema.Grid.MARGINS_DP.name()));
    }
}
