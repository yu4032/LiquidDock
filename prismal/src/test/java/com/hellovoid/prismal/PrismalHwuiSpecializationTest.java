package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrismalHwuiSpecializationTest {
    @Test public void workspaceAlwaysUsesPreparedBlurWithoutRuntimeTextureSelection() {
        for (int variant = 0; variant < 3; variant++) {
            String generic = PrismalRenderer.workspaceHwuiProbeFragmentSource(
                    variant != 0, variant == 2);
            assertTrue(generic.contains("u_useBlurredTexture == 1"));
            String specialized = PrismalRenderer.specializeWorkspaceHwuiSource(generic);
            assertFalse(specialized.contains("u_useBlurredTexture == 1"));
            assertTrue(specialized.matches("(?s).*uniform\\s+int\\s+u_useBlurredTexture;.*"));
        }
    }

    @Test public void hwuiProgramsSpecializeOnlyFamiliesDisabledByOs4Replacement() {
        PrismalHighlightProfile profile = PrismalHighlightProfile.ALL_ENABLED
                .withOs4EdgeReplacingLegacyEdge();
        assertFalse(profile.specular || profile.litRim || profile.oppositeRim
                || profile.cornerRim || profile.faceSheen || profile.plainHighlight);
        assertTrue(profile.skyHaze && profile.caustics && profile.pressGlow && profile.os4Edge);
        for (int variant = 0; variant < 3; variant++) {
            String generic = PrismalRenderer.workspaceHwuiProbeFragmentSource(
                    variant != 0, variant == 2);
            assertTrue(generic.contains("u_componentSpecular > 0.5"));
            String shader = PrismalRenderer.specializeWorkspaceHwuiSource(generic);
            for (String family : new String[]{"Specular", "LitRim", "OppositeRim",
                    "CornerRim", "FaceSheen", "PlainHighlight"}) {
                assertFalse(shader.contains("u_component" + family + " > 0.5"));
                // Retain the shared uniform binding interface, including inactive families.
                assertTrue(shader.contains("uniform float u_component" + family + ";"));
            }
            assertTrue(shader.contains("u_componentSkyHaze > 0.5"));
            assertTrue(shader.contains("u_componentCaustics > 0.5"));
            assertTrue(shader.contains("u_componentPressGlow > 0.5"));
        }
    }
}
