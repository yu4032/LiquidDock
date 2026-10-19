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
        int finalBlend = source.indexOf("vec3 combinedRgb = (contourGlass * glassAlpha");
        assertTrue(line > 0);
        assertTrue(originalDiscard < 0); // early discard replaced by exterior paint path
        assertTrue(reflected > line);
        assertTrue(finalBlend > reflected);
        assertTrue(source.contains("float surfaceDistance = distMask + insetShiftPx;"));
        assertTrue(source.contains("float inkDistance = surfaceDistance + u_backlitViewportInsetPx;"));
        assertTrue(source.contains("vec3 refractedContourColor(vec3 transmitted"));
        assertTrue(source.contains("vec2 rimUv = backdropUv(v_screenTexCoord"));
        assertTrue(source.contains("vec3 contourGlass = mix("));
        assertTrue(source.contains("vec3 rimBase = mix(edgeTransmission, color,"));
        assertTrue(source.contains("vec3 rim = refractedContourColor("));
        assertTrue(source.contains("vec3 edgeTransmission = vec3(0.0);"));
        assertTrue(source.contains("smoothstep(0.55, 1.0, opacity)"));
        assertFalse(source.contains("vec3(0.015)"));
        assertTrue(source.contains("if (inkDistance > 0.0 && u_backlitStrength > 0.0"));
        assertTrue(source.contains("if (surfaceDistance > 0.0 && u_castShadowRadiusPx > 0.0"));
        // The Dock-specific inset moves only ink sampling, not the whole
        // body geometry nor the cast shadow boundary.
        assertFalse(source.contains("float surfaceDistance = distMask + insetShiftPx +"));
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
                source.indexOf("// The rim uses one identically displaced prepared backdrop"));
        assertTrue(ink.contains("dot(edgeNormal, u_invariantLightXY)"));
        assertTrue(ink.contains("float lineWidthPx = u_backlitWidthPx;"));
        assertTrue(ink.contains("rimDarkWeight = smoothstep("));
        assertFalse(ink.contains("u_backlitStrength * lightEnergy * taper"));
        assertTrue(ink.contains("if (inkDistance > 0.0"));
        assertTrue(ink.contains("lineWidthPx + outsideAa, inkDistance)"));
        assertTrue(ink.contains("float entry = smoothstep(0.0, outsideAa,"));
        assertTrue(ink.contains("float exit = 1.0 - smoothstep("));
        assertFalse(ink.contains("sdRoundBox("));
        assertFalse(ink.contains("texture2D("));
        assertFalse(ink.contains("abs(edgeNormal.x)"));
        assertFalse(ink.contains("gl_FragColor"));
        assertFalse(ink.contains("u_os4EdgeEnabled > 0.5"));
        assertTrue(ink.contains("u_backlitGrazingStart + u_backlitGrazingSpan"));
        assertFalse(ink.contains("float quick ="));
        assertFalse(ink.contains("float gradual ="));
        assertFalse(ink.contains("taper ="));
        assertFalse(ink.contains("float transitionWidth = mix("));
        assertTrue(source.contains("uniform float u_backlitGrazingStart;"));
        assertTrue(source.contains("uniform float u_backlitGrazingSpan;"));
        assertTrue(ink.contains("float grazing = clamp(1.0 - angularIncidence"));
        assertFalse(ink.contains("fadeStart"));
        assertFalse(ink.contains("fadeEnd"));
    }

    @Test public void zeroExtentDisablesTheOptionalRimButShadowStillWorks() {
        PrismalEdgeDecoration zero = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertFalse(zero.hasBacklit());
        assertEquals(0f, zero.extraGuardPx(3f), 0f);
        PrismalEdgeDecoration shadow = new PrismalEdgeDecoration(
                1f, .85f, .6f, 15f, 3f, 0f, 0f, 0f, .25f, .65f, 0f);
        assertFalse(shadow.hasBacklit());
        assertTrue(shadow.hasShadow());
    }

    @Test public void measuredReferenceCornerDelaysDarkTransitionToSeventyFiveDegrees() {
        PrismalEdgeDecoration material = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, .55f);
        float start = material.backlitGrazingStart();
        float span = material.backlitGrazingSpan();
        assertEquals(.739f, start, .005f);
        assertEquals(.0765f, span, .0001f);
        assertTrue(weightAtDegrees(70, material) < .05f);
        assertTrue(weightAtDegrees(75, material) < .10f);
        assertTrue(weightAtDegrees(78, material) > .15f);
        assertTrue(weightAtDegrees(80, material) > .90f);
    }

    @Test public void extentChangesDarkStartAndSoftnessOnlyChangesTransitionSpan() {
        float lastStart = Float.POSITIVE_INFINITY;
        for (float e : new float[] {.001f, .1f, .25f, .55f, .8f, 1f}) {
            PrismalEdgeDecoration sharp = new PrismalEdgeDecoration(
                    1f, .85f, .05f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, e);
            PrismalEdgeDecoration soft = new PrismalEdgeDecoration(
                    1f, .85f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, e);
            assertTrue(sharp.backlitGrazingStart() <= lastStart);
            assertEquals(sharp.backlitGrazingStart(), soft.backlitGrazingStart(), .000001f);
            assertTrue(soft.backlitGrazingSpan() > sharp.backlitGrazingSpan());
            assertEquals(0f, lobe(sharp.backlitGrazingStart(),
                    sharp.backlitGrazingStart(), sharp.backlitGrazingSpan()), .00001f);
            lastStart = sharp.backlitGrazingStart();
        }
    }

    private static float weightAtDegrees(int degrees, PrismalEdgeDecoration material) {
        float grazing = 1f - (float) Math.cos(Math.toRadians(degrees));
        return lobe(grazing, material.backlitGrazingStart(), material.backlitGrazingSpan());
    }

    private static float lobe(float grazing, float start, float span) {
        return smooth((grazing - start) / span);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t*t*(3f-2f*t);
    }

    @Test public void dockViewportAdaptationDoesNotDistortBodyGeometry() {
        PrismalEdgeDecoration style = new PrismalEdgeDecoration(
                1f, .85f, .6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        PrismalEdgeDecoration dock = style.fitInkToNativeViewport(1.5f);
        assertEquals(1.5f, dock.inkViewportInsetPx, .00001f);
        assertEquals(0f, style.inkViewportInsetPx, .00001f);
        assertEquals(style.backlitWidthDp, dock.backlitWidthDp, .00001f);
        assertEquals(style.backlitExtent, dock.backlitExtent, .00001f);
        assertFalse(dock.withoutCastShadow().hasShadow());
        assertEquals(1.5f, dock.withoutCastShadow().inkViewportInsetPx, .00001f);
        assertSame(PrismalEdgeDecoration.NONE,
                PrismalEdgeDecoration.NONE.fitInkToNativeViewport(1.5f));
    }


    @Test public void optionalOpticsCannotReferenceBlockScopedLegacyRimColor() {
        // PrismalComponentGateShader wraps the legacy highlight family
        // (including hiSoft) in a conditional block. Both shadow-only and
        // contour-enabled optical shaders must remain valid even when those
        // legacy highlights are disabled by the OS4 profile.
        String source = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(source.contains(
                "const vec3 OS4_OPTICAL_RIM_COLOR = vec3(0.98, 0.992, 1.008);"));
        int legacyDefinition = source.indexOf("vec3 hiSoft =");
        int opticalFinal = source.lastIndexOf(
                "vec3 rim = refractedContourColor(");
        assertTrue("legacy highlight stays before the optical output",
                legacyDefinition > 0 && opticalFinal > legacyDefinition);
        assertFalse("no out-of-scope hiSoft reference in optional material",
                source.substring(opticalFinal).contains("hiSoft"));
        assertTrue(source.substring(opticalFinal).contains("OS4_OPTICAL_RIM_COLOR"));
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

    @Test public void globalColorHelperDoesNotUseLaterDeclaredUniforms() {
        // OS4 light uniforms are inserted further down the fragment source,
        // whereas this shared helper is injected directly after precision.
        // Referring to those uniforms inside the helper would break some GLSL
        // ES 1.00 compilers even though a Java unit test assembles the string.
        String shader = PrismalEdgeDecorationShader.apply(upstream());
        int helperStart = shader.indexOf("vec3 refractedContourColor(");
        int helperEnd = shader.indexOf("return clamp(transmitted * transmission", helperStart);
        assertTrue(helperStart >= 0 && helperEnd > helperStart);
        String helper = shader.substring(helperStart, helperEnd);
        assertFalse(helper.contains("u_os4DirectionalIntensity"));
        assertFalse(helper.contains("u_os4DirectionalOppositeIntensity"));
        assertTrue(shader.contains("float rimLightEnergy = clamp("));
        assertTrue(shader.contains("rimDarkWeight, rimLightEnergy);"));
    }

    @Test public void insideAndOutsideContourUseOnePreparedBackdropSignal() {
        String source = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(source.contains("vec3 edgeTransmission = vec3(0.0);"));
        assertTrue(source.contains(
                "edgeTransmission = texture2D(u_backgroundTexture, rimUv).rgb;"));
        assertTrue(source.contains("refractedContourColor(edgeTransmission,"));
        assertTrue(source.contains("vec3 rimBase = mix(edgeTransmission, color,"));
        assertTrue(source.contains("smoothstep(0.55, 1.0, opacity)"));
        assertEquals("edge sample must be shared between both branches",
                1, occurrences(source, "vec2 rimUv = backdropUv("));
        assertEquals("one full-detail source sample shared across both edge branches",
                1, occurrences(source, "texture2D(u_backgroundTexture, rimUv)"));
        assertEquals("no Gaussian-blurred optical-rim sample",
                0, occurrences(source, "texture2D(u_blurredTexture, rimUv)"));
        assertTrue(source.indexOf("vec3 edgeTransmission = vec3(0.0);")
                < source.indexOf("if (opacity < 0.001) {"));
    }

    private static int occurrences(String s, String token) {
        int count = 0;
        for (int pos = 0; (pos = s.indexOf(token, pos)) >= 0; pos += token.length())
            count++;
        return count;
    }

    @Test public void opticalRimUsesSourcePixelsWhileMainGlassKeepsItsBlur() {
        String source = PrismalEdgeDecorationShader.apply(upstream());
        int begin = source.indexOf("// The SDF is already native resolution");
        int end = source.indexOf("// Cast shadow remains below", begin);
        assertTrue(begin >= 0 && end > begin);
        String contour = source.substring(begin, end);
        assertTrue(contour.contains("texture2D(u_backgroundTexture, rimUv)"));
        assertFalse(contour.contains("texture2D(u_blurredTexture, rimUv)"));
        assertFalse(contour.contains("u_useBlurredTexture == 1"));
        assertTrue(source.contains("smoothstep(0.55, 1.0, opacity)"));
        assertTrue(source.contains("texture2D(u_blurredTexture, uvCenter)"));
    }

    @Test public void materialTintPreservesSourceHueInsteadOfWhiteBlackEndpoints() {
        String source = PrismalEdgeDecorationShader.apply(upstream());
        assertTrue(source.contains("float transmission = mix(1.0, 0.12, darkWeight);"));
        assertTrue(source.contains("vec3 specTint = mix(transmitted, highlight, 0.25);"));
        assertFalse(source.contains("transmitted * 0.035"));
        assertFalse(source.contains("max(transmitted, highlight), 0.88"));

        float[] warm = {.70f, .42f, .24f};
        float[] highlight = {.98f, .992f, 1.008f};
        float[] bright = new float[3], dark = new float[3];
        for (int i=0;i<3;i++) {
            bright[i]=contourColor(warm[i], highlight[i], 0f);
            dark[i]=contourColor(warm[i], highlight[i], 1f);
        }
        assertTrue("bright rim retains red-green chroma", bright[0]-bright[1] > .20f);
        assertTrue("dark rim remains warm instead of neutral black", dark[0] > dark[1]);
        assertEquals(warm[0]/warm[2], dark[0]/dark[2], .001f);
    }

    @Test public void refractionDrivesBothBrightAndDarkContourRgb() {
        float brown = .49f, gray = .42f, os4White = .98f;
        assertTrue(contourColor(brown, os4White, 0f) > brown);
        assertTrue(contourColor(gray, os4White, 0f) > gray);
        assertEquals(brown * .12f, contourColor(brown, os4White, 1f), .0001f);
        assertEquals(gray * .12f, contourColor(gray, os4White, 1f), .0001f);
        assertNotEquals("dark color is sampled, not constant ink",
                contourColor(brown, os4White, 1f),
                contourColor(gray, os4White, 1f), .0001f);
        float previous = contourColor(brown, os4White, 0f);
        for(int i = 1; i <= 1000; i++) {
            float value = contourColor(brown, os4White, i / 1000f);
            assertTrue(value <= previous + .00001f);
            assertTrue(previous - value < .005f);
            previous = value;
        }
    }
    private static float contourColor(float transmitted, float highlight, float darkWeight) {
        float transmission = 1f*(1f-darkWeight) + .12f*darkWeight;
        float lightEnergy = 1f; // representative default profile
        float specGain = (.10f + .18f*lightEnergy)*(1f-darkWeight);
        float tint = transmitted*.75f + highlight*.25f;
        return Math.min(1f, Math.max(0f, transmitted*transmission + tint*specGain));
    }
}

