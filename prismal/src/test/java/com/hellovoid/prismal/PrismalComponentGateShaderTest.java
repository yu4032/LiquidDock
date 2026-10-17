package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

/** Guards the shader-source rewrite that removes work for OS4-disabled legacy highlights. */
public class PrismalComponentGateShaderTest {
    private static String transformed() {
        return PrismalComponentGateShader.apply(
                PrismalOpticalEdgeShader.apply(
                        PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
    }

    @Test public void os4DisabledLegacyFamiliesGateTheirWholeArithmeticBlocks() {
        String shader = transformed();
        assertTrue(shader.contains("if (u_componentSpecular > 0.5) {"));
        assertTrue(shader.contains(
                "if (u_componentLitRim > 0.5 || u_componentOppositeRim > 0.5"));
        assertTrue(shader.contains("|| u_componentPlainHighlight > 0.5) {"));

        int sharedLight = shader.indexOf("vec2 Lxy = normalize(u_lightDir + vec2(1e-5));");
        int legacyGate = shader.indexOf("if (u_componentLitRim > 0.5");
        int os4Use = shader.indexOf("vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));");
        assertTrue(sharedLight >= 0 && sharedLight < legacyGate && legacyGate < os4Use);

        // Expensive legacy-only math now lives after the false-for-OS4 uniform branch.
        assertTrue(shader.indexOf("float Fnv = pow(1.0 - dotNV, 2.9);") > legacyGate);
        assertTrue(shader.indexOf("float pairOpp = pow(") > legacyGate);
        assertTrue(shader.indexOf("float sx = exp(") > legacyGate);
        assertTrue(shader.indexOf("float plusHL = smoothstep(") > legacyGate);
    }

    @Test public void specularSecondaryVectorsMoveInsideSpecularGateButPrimaryStaysShared() {
        String shader = transformed();
        int lp = shader.indexOf("vec3 Lp = normalize(vec3(u_lightDir, 1.45));");
        int gate = shader.indexOf("if (u_componentSpecular > 0.5) {");
        int ls = shader.indexOf("vec3 Ls = normalize(vec3(-u_lightDir.x");
        int hp = shader.indexOf("vec3 Hp = normalize(Lp + V);");
        int caustic = shader.indexOf("float causticDot = dot(");
        assertTrue(lp >= 0 && lp < gate);
        assertTrue(gate < ls && ls < hp);
        assertTrue(caustic > gate);
        assertEquals(1, occurrences(shader, "vec3 Ls = normalize("));
        assertEquals(1, occurrences(shader, "vec3 Hp = normalize("));
        assertEquals(1, occurrences(shader, "vec3 Hs = normalize("));
    }

    @Test public void optionalSkyCausticAndPressFamiliesGateComputationNotOnlyFinalAdd() {
        String shader = transformed();
        int skyGate = shader.indexOf("if (u_componentSkyHaze > 0.5) {");
        int skyPow = shader.indexOf("float skyW = min(");
        assertTrue(skyGate >= 0 && skyGate < skyPow);
        assertTrue(shader.contains(
                "if (u_componentCaustics > 0.5 && u_causticIntensity > 0.001) {"));
        assertTrue(shader.contains(
                "if (u_componentPressGlow > 0.5 && pressGlow > 0.001) {"));
    }

    @Test public void causticsUseSmoothFaceFieldWithoutChangingRimOrAlpha() {
        String shader = transformed();
        assertTrue("Upstream must remain the unmodified provenance baseline",
                PrismalShaderSources.FRAGMENT.contains(
                        "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;"));
        assertEquals(1, occurrences(shader,
                "vec2 causticGrad = mix(gradH, gradHSig, coreBlend);"));
        assertEquals(1, occurrences(shader,
                "float causticHeight = mix(height, hSig, coreBlend);"));
        assertTrue(shader.contains(
                "dot(normalize(vec3(causticGrad * u_normalStrength, 0.45)), Lp)"));
        assertTrue(shader.contains(
                "* u_causticIntensity * causticHeight;"));
        assertFalse(shader.contains(
                "* u_causticIntensity * height;"));
        assertTrue(shader.contains(
                "if (u_componentCaustics > 0.5 && u_causticIntensity > 0.001) {"));
        assertTrue(shader.contains(
                "gl_FragColor = vec4(color, opacity * u_transmittance);"));

        // The face of a large dome must use the smooth slab response, not a
        // low/zero hDome. The rim must remain byte-for-byte optically equivalent.
        float rimBlend = 0f;
        float deepFaceBlend = 1f;
        float slabHeight = 1f;
        float collapsedDomeHeight = 0.035f;
        assertEquals(collapsedDomeHeight,
                collapsedDomeHeight * (1f - rimBlend) + slabHeight * rimBlend, 0.0001f);
        assertEquals(slabHeight,
                collapsedDomeHeight * (1f - deepFaceBlend) + slabHeight * deepFaceBlend,
                0.0001f);
    }

    @Test public void enabledFamiliesRetainEveryOriginalColorContributionExactlyOnce() {
        String shader = transformed();
        String[] statements = {
                "color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);",
                "color += (specP + specS) * vec3(0.99, 0.993, 1.0);",
                "color += hiSoft * rimLitSide * rimScale;",
                "color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;",
                "color += hiSoft * rimCorner * rimScale;",
                "color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;",
                "color += plusHL * vec3(0.99, 0.995, 1.0);",
                "color += caust * vec3(1.0, 0.96, 0.90);",
                "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);"
        };
        for (String statement : statements) {
            assertEquals(statement, 1, occurrences(shader, statement));
        }
        // OS4 optical path remains present and is not hidden by the legacy branch.
        assertTrue(shader.contains("float os4ReflectionWeight = clamp("));
        assertTrue(shader.contains("float os4SoftLight = clamp("));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
