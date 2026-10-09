package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Independent profiles inherit global optics until that scope is explicitly overridden. */
public class ScopedGlassOpticsTest {
    @Test public void threeScopesHaveDisjointKeysAndSameOpticalCatalog() {
        String g = ScopedGlassOptics.key(ScopedGlassOptics.GBOARD, ConfigSchema.Glass.LENS_REFRACTION);
        String s = ScopedGlassOptics.key(ScopedGlassOptics.SEARCHBOX, ConfigSchema.Glass.LENS_REFRACTION);
        String d = ScopedGlassOptics.key(ScopedGlassOptics.DIALOG, ConfigSchema.Glass.LENS_REFRACTION);
        assertFalse(g.equals(s));
        assertFalse(s.equals(d));
        assertFalse(g.equals(d));
        assertTrue(g.startsWith("liquid_"));
        assertTrue(ScopedGlassOptics.opticalKeys().size() >= 40);
    }

    @Test public void oneScopeDoesNotChangeAnotherOrGlobalOptics() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.LENS_REFRACTION.name(), 1);
        values.put(ConfigSchema.Glass.LENS_REFRACTION.name() + "_tenths", 13);
        values.put(
                ScopedGlassOptics.key(ScopedGlassOptics.GBOARD,
                        ConfigSchema.Glass.LENS_REFRACTION) + "_tenths", 25);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass gboard = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.GBOARD);
        LiquidDockConfig.Glass search = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.SEARCHBOX);
        LiquidDockConfig.Glass dialog = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.DIALOG);
        assertEquals(1.3f, global.lensRefraction, 0.0001f);
        assertEquals(2.5f, gboard.lensRefraction, 0.0001f);
        assertEquals(1.3f, search.lensRefraction, 0.0001f);
        assertEquals(1.3f, dialog.lensRefraction, 0.0001f);
    }
}
