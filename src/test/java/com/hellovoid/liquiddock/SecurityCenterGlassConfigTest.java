package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SecurityCenterGlassConfigTest {
    @Test
    public void securityCenterGlassSchemaUsesDisabledAlwaysExportedDefaults() {
        ConfigKey<Boolean> key = ConfigSchema.Glass.SECURITY_CENTER_GLASS;

        assertEquals("liquid_security_center_glass", key.name());
        assertEquals(Boolean.FALSE, key.uiDefault());
        assertEquals(Boolean.FALSE, key.runtimeFallback());
        assertEquals(Boolean.FALSE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void securityCenterGlassBooleanRoundTripsThroughConfigCodec() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(), true);

        Map<String, Object> imported = ConfigCodec.importValues(
                ConfigCodec.exportValues(preferences));

        assertEquals(Boolean.TRUE, imported.get(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()));
    }

    @Test
    public void defaultPresetKeepsSecurityCenterGlassDisabled() {
        assertEquals(Boolean.FALSE,
                PresetManager.defaultValues().get(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()));
    }

    @Test
    public void typedConfigReadsSecurityCenterGlassFromReader() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(), true);

        assertTrue(LiquidDockConfig.from(new ConfigReader(preferences)).glass.securityCenterEnabled);
        assertFalse(LiquidDockConfig.from(new ConfigReader(new HashMap<>()))
                .glass.securityCenterEnabled);
    }
}
