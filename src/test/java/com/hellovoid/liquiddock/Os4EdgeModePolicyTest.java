package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Mutual-exclusion contract between the OS4 soft edge and legacy highlight stack. */
public class Os4EdgeModePolicyTest {
    @Test
    public void schemaDefaultsOs4SoftEdgeOn() {
        assertTrue(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.uiDefault());
        assertTrue(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.runtimeFallback());
        assertTrue(ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.exportDefault());
    }

    @Test
    public void os4ModeSuppressesAllLegacyHighlightComponentsAtRuntime() {
        PrismalHighlightProfile configured = new PrismalHighlightProfile(
                true, false, true, false, true, false, true, false, true);
        PrismalHighlightProfile effective =
                Os4EdgeModePolicy.effectiveHighlights(true, configured);
        assertFalse(effective.skyHaze);
        assertFalse(effective.specular);
        assertFalse(effective.litRim);
        assertFalse(effective.oppositeRim);
        assertFalse(effective.cornerRim);
        assertFalse(effective.faceSheen);
        assertFalse(effective.plainHighlight);
        assertFalse(effective.caustics);
        assertFalse(effective.pressGlow);
    }

    @Test
    public void legacyModeRestoresConfiguredHighlightProfileWithoutMutation() {
        PrismalHighlightProfile configured = new PrismalHighlightProfile(
                false, true, false, true, false, true, false, true, false);
        assertSame(configured, Os4EdgeModePolicy.effectiveHighlights(false, configured));
    }

    @Test
    public void uiVisibilityIsMutuallyExclusive() {
        assertTrue(Os4EdgeModePolicy.showOs4Controls(true));
        assertFalse(Os4EdgeModePolicy.legacyHighlightsEnabled(true));
        assertFalse(Os4EdgeModePolicy.showOs4Controls(false));
        assertTrue(Os4EdgeModePolicy.legacyHighlightsEnabled(false));
    }

    @Test
    public void liquidConfigAppliesModeToBothCompactAndLargeProfiles() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_os4_soft_edge", true);
        values.put("launcher_surface_component_specular", true);
        values.put("launcher_large_surface_component_specular", true);
        LiquidDockConfig os4 = LiquidDockConfig.from(new ConfigReader(values));
        assertTrue(os4.glass.os4SoftEdgeEnabled);
        assertFalse(os4.glass.launcherHighlightProfile.specular);
        assertFalse(os4.glass.largeSurfaceHighlightProfile.specular);

        values.put("liquid_os4_soft_edge", false);
        LiquidDockConfig legacy = LiquidDockConfig.from(new ConfigReader(values));
        assertFalse(legacy.glass.os4SoftEdgeEnabled);
        assertTrue(legacy.glass.launcherHighlightProfile.specular);
        assertTrue(legacy.glass.largeSurfaceHighlightProfile.specular);
    }

    @Test
    public void modeFlagReachesPortablePrismalParams() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_os4_soft_edge", false);
        LiquidDockConfig legacy = LiquidDockConfig.from(new ConfigReader(values));
        PrismalParams legacyParams = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.fromConfig(legacy.glass, 1f));
        assertFalse(legacyParams.os4SoftEdgeEnabled);

        values.put("liquid_os4_soft_edge", true);
        LiquidDockConfig os4 = LiquidDockConfig.from(new ConfigReader(values));
        PrismalParams os4Params = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.fromConfig(os4.glass, 1f));
        assertTrue(os4Params.os4SoftEdgeEnabled);
    }
}
