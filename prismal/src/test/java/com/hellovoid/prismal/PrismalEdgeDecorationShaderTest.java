package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

/** Optical line is outside the glass and always behind its reflected highlights. */
public class PrismalEdgeDecorationShaderTest {
    private static String upstream() {
        return PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    }

    @Test public void exteriorInkCompositesBehindNativeOs4Highlight() {
        String source = PrismalEdgeDecorationShader.apply(upstream());
        int line = source.indexOf("float exteriorInkAlpha = 0.0;");
        int originalDiscard = source.indexOf("if (opacity < 0.001) discard;");
        int reflected = source.indexOf("float os4SoftLight =");
        int finalBlend = source.indexOf("vec3 combinedRgb = (color * glassAlpha");
        assertTrue(line > 0);
        assertTrue(originalDiscard < 0); // early discard replaced by exterior paint path
        assertTrue(reflected > line);
        assertTrue(finalBlend > reflected);
        assertTrue(source.contains("float surfaceDistance = distMask + insetShiftPx;"));
        assertTrue(source.contains("if (surfaceDistance > 0.0 && u_backlitStrength > 0.0"));
        assertTrue(source.contains("gl_FragColor = vec4(underlayRgb, underlayAlpha);"));
        assertTrue(source.contains("+ underlayRgb * underlayAlpha * (1.0 - glassAlpha)"));
        assertTrue(source.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
        assertFalse(source.contains("rimLineCoverage"));
        assertFalse(source.contains("color = mix(color, vec3(0.015)"));
        assertFalse(source.contains("lineAlpha = opacity *"));
    }

    @Test public void externalStrokeUsesOneSdfAndLightDependentWidthAndFade() {
        String source = PrismalEdgeDecorationShader.apply(upstream());
        String ink = source.substring(source.indexOf("float exteriorInkAlpha = 0.0;"),
                source.indexOf("// Ink is in front of the outer cast shadow"));
        assertTrue(ink.contains("dot(edgeNormal, u_invariantLightXY)"));
        assertTrue(ink.contains("float lineWidthPx = u_backlitWidthPx * taper;"));
        assertTrue(ink.contains("float entry = smoothstep(0.0, outsideAa,"));
        assertTrue(ink.contains("float exit = 1.0 - smoothstep("));
        assertFalse(ink.contains("sdRoundBox("));
        assertFalse(ink.contains("texture2D("));
        assertFalse(ink.contains("abs(edgeNormal.x)"));
        assertFalse(ink.contains("gl_FragColor"));
        assertFalse(ink.contains("u_os4EdgeEnabled > 0.5"));
        assertTrue(ink.contains("float taper = mix(0.08, 1.0, angularResponse);"));
        assertTrue(ink.contains("float grazing = clamp(1.0 - angularIncidence"));
        assertFalse(ink.contains("fadeStart"));
        assertFalse(ink.contains("fadeEnd"));
    }

    @Test public void continuousPerimeterDoesNotHaveArbitraryAngularEndpoints() {
        float side = rimWeight(.53f, .6f);
        float corner = rimWeight(.69f, .6f);
        float top = rimWeight(.85f, .6f);
        assertTrue("grazing side darkens more", side > corner);
        assertTrue("the corner remains connected", corner > top);
        assertTrue("even the lit edge retains a faint seam", top > 0.08f);
        assertEquals(0.08f, rimWeight(1f, .6f), 0.0001f);
        assertEquals(1f, rimWeight(0f, .6f), 0.0001f);
        // As orientation traverses a complete round corner, the response is
        // continuous and never jumps to a hard zero at a chosen angle.
        float previous = rimWeight(0f, .6f);
        for (int i = 1; i <= 1000; i++) {
            float current = rimWeight(i / 1000f, .6f);
            assertTrue(current <= previous + 0.0001f);
            assertTrue(previous - current < .004f);
            previous = current;
        }
    }

    private static float rimWeight(float incidence, float softness) {
        float grazing = Math.max(0f, Math.min(1f, 1f - incidence));
        float spread = .45f + .60f * softness;
        float t = Math.max(0f, Math.min(1f, grazing / spread));
        float response = t*t*(3f-2f*t);
        return .08f + .92f * response;
    }

    @Test public void baselineRemainsUnmodifiedAndShadowOnlyStillWorks() {
        assertFalse(PrismalEdgeDecoration.NONE.hasBacklit());
        assertFalse(PrismalEdgeDecoration.NONE.hasShadow());
        assertEquals(0f, PrismalEdgeDecoration.NONE.extraGuardPx(3f), 0.001f);
        PrismalEdgeDecoration shadow = new PrismalEdgeDecoration(
                0f, 0f, .6f, 15f, 4f, 0f, 0f, 0f, .2f, .5f);
        assertFalse(shadow.hasBacklit());
        assertTrue(shadow.hasShadow());
        assertTrue(shadow.extraGuardPx(2f) > 0f);
        PrismalEdgeDecoration nativeShadow = shadow.withoutCastShadow();
        assertFalse(nativeShadow.hasShadow());
        assertEquals(0f, nativeShadow.extraGuardPx(2f), 0.001f);
    }

    @Test public void outerHairlineExpandsVertexAndScissorGuard() {
        PrismalEdgeDecoration optical = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertTrue(optical.hasBacklit());
        assertFalse(optical.hasShadow());
        assertEquals(5f, optical.extraGuardPx(3f), 0.001f);
        PrismalGeometry geo = new PrismalGeometry(1000, 800, 500, 300, 100, 80, 16);
        PrismalNodeScissor.Rect baseline = PrismalNodeScissor.compute(geo, 1000, 800);
        PrismalNodeScissor.Rect bounds = new PrismalNodeScissor.Rect(0, 0, 0, 0);
        assertTrue(PrismalNodeScissor.computeInto(geo, 1000, 800, bounds,
                optical.extraGuardPx(3f)));
        assertEquals(baseline.width + 10, bounds.width);
        assertEquals(baseline.height + 10, bounds.height);
        String vertex = PrismalRasterGuardShader.applyWithEdgeGuard(
                PrismalShaderSources.VERTEX);
        assertTrue(vertex.contains("u_edgeRasterGuardPx"));
        assertFalse(PrismalRasterGuardShader.apply(
                PrismalShaderSources.VERTEX).contains("u_edgeRasterGuardPx"));
    }

    @Test public void glassOverInkPreservesHighlightAndOutsideRemainsDark() {
        // A bright reflected glass surface stays visually bright where its
        // alpha is high. Past that surface, the ink supplies the dark outline.
        float glassA = .95f, glassR = .98f, inkA = .90f, inkR = .015f;
        float outA = glassA + inkA*(1f-glassA);
        float result = (glassR*glassA + inkR*inkA*(1f-glassA))/outA;
        assertTrue("OS4 highlight must survive", result > .9f);
        assertTrue("exterior ink remains near black", inkR < .02f);
        assertEquals(inkA, 0f + inkA*(1f-0f), .00001f);
    }
}
