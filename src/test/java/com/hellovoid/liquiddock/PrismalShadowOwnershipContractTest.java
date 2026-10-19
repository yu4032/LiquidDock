package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalEdgeDecoration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Use typed production config/policy contracts, not source parsing, to verify
 * the glass-only shadow-owner transition.
 */
public final class PrismalShadowOwnershipContractTest {
    private static PrismalEdgeDecoration style(String scope, boolean shadow) {
        Map<String,Object> cfg = new HashMap<>();
        cfg.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), shadow);
        return GlassEdgeEffects.resolve(new ConfigReader(cfg), scope);
    }

    @Test public void allGlassScopesHonorTheSameTypedPrismalShadowOption() {
        for (String scope : GlassEdgeEffects.SCOPES) {
            assertTrue(scope + " must honor enabled GPU cast shadow",
                    style(scope, true).hasShadow());
            assertFalse(scope + " must not paint shadows when disabled",
                    style(scope, false).hasShadow());
        }
        // Optically fit the Dock contour but never delete its shadow recipe.
        PrismalEdgeDecoration dock = GlassEdgeEffects.resolve(
                new ConfigReader(new HashMap<>()), GlassEdgeEffects.DOCK);
        assertFalse(dock.hasShadow());
    }

    @Test public void nativeDockShadowIsAuthoritativeOutsideActiveGlass() {
        assertFalse(DockShadowRuntimePolicy.shouldSuppressNativeForPrismal(
                false,true,true));
        assertFalse(DockShadowRuntimePolicy.shouldSuppressNativeForPrismal(
                true,false,true));
        assertFalse(DockShadowRuntimePolicy.shouldSuppressNativeForPrismal(
                true,true,false));
        assertTrue(DockShadowRuntimePolicy.shouldSuppressNativeForPrismal(
                true,true,true));
    }

    @Test public void scopedShadowToggleCanDisableOneComponentWithoutDisablingOthers() {
        Map<String,Object> cfg = new HashMap<>();
        cfg.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        cfg.put(GlassEdgeEffects.key(GlassEdgeEffects.DOCK,
                ConfigSchema.Glass.OUTER_SHADOW_ENABLED), false);
        ConfigReader reader = new ConfigReader(cfg);
        assertFalse(GlassEdgeEffects.resolve(reader, GlassEdgeEffects.DOCK).hasShadow());
        assertTrue(GlassEdgeEffects.resolve(reader, GlassEdgeEffects.ICON).hasShadow());
        assertTrue(GlassEdgeEffects.resolve(reader, GlassEdgeEffects.WIDGET).hasShadow());
    }
}
