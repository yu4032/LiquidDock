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
        assertTrue(source.contains("vec3 rim = refractedContourColor(color, hiSoft, rimDarkWeight);"));
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
                source.indexOf("// Ink is in front of the outer cast shadow"));
        assertTrue(ink.contains("dot(edgeNormal, u_invariantLightXY)"));
        assertTrue(ink.contains("float lineWidthPx = u_backlitWidthPx;"));
        assertTrue(ink.contains("rimDarkWeight = taper;"));
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
        assertTrue(ink.contains("float reachStart = mix(1.0, -0.20, u_backlitExtent);"));
        assertTrue(ink.contains("float softness = clamp(u_backlitSoftness * 0.5"));
        assertTrue(ink.contains("float gradual = smoothstep(0.0, 1.0, progress);"));
        assertTrue(ink.contains("taper = max(taper, 0.18 * e4 * e4);"));
        assertFalse(ink.contains("float taper = mix(0.04"));
        assertFalse(ink.contains("float transitionWidth = mix("));
        assertTrue(source.contains("uniform float u_backlitExtent;"));
        assertTrue(ink.contains("float grazing = clamp(1.0 - angularIncidence"));
        assertFalse(ink.contains("fadeStart"));
        assertFalse(ink.contains("fadeEnd"));
    }

    @Test public void zeroReachHasNoHiddenFullPerimeterRim() {
        PrismalEdgeDecoration zero = new PrismalEdgeDecoration(
                1f, .85f, .60f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        assertFalse(zero.hasBacklit());
        assertEquals(0f, zero.extraGuardPx(3f), 0f);
        for (int i=0;i<=1000;i++)
            assertEquals(0f, rimWeight(i/1000f, .6f, 0f), 0f);
        // Near zero, the visible angle is genuinely narrow.
        assertEquals(0f, rimWeight(.53f, .6f, .08f), .000001f);
        assertEquals(0f, rimWeight(.85f, 2f, .08f), .000001f);
        assertTrue(rimWeight(.001f, .6f, .08f) > 0f);
    }

    @Test public void extentControlsReachIndependentlyFromSoftness() {
        float shortCorner = rimWeight(.69f, .6f, .1f);
        float longCorner = rimWeight(.69f, .6f, .8f);
        assertEquals(0f, shortCorner, .0001f);
        assertTrue(longCorner > .1f);
        assertTrue(rimWeight(.53f, .6f, .75f)
                > rimWeight(.53f, .6f, .25f));
        // 100% spans full circle, including a faint seam at exact face-light.
        assertTrue(rimWeight(1f, 2f, 1f) >= .18f - .0001f);
        assertEquals(1f, rimWeight(0f, .6f, 1f), .0001f);
    }

    @Test public void twoHundredPercentSoftnessIsMoreGradualButNotLonger() {
        float normal = .45f, reach = .55f;
        float sharp = rimWeight(normal, .05f, reach);
        float soft = rimWeight(normal, 2f, reach);
        assertTrue(sharp > soft);
        assertTrue(soft > 0f);
        // The deliberately tiny high-extent floor is independent of taper
        // softness when the normal is outside the adjustable visible reach.
        assertEquals(rimWeight(.95f, .05f, reach),
                rimWeight(.95f, 2f, reach), .0000001f);
        for(float extent:new float[]{0f,.1f,.55f,.9f,1f}) {
            float prev=rimWeight(0f,2f,extent);
            for(int i=1;i<=1000;i++){
                float curr=rimWeight(i/1000f,2f,extent);
                assertTrue(curr <= prev + .00001f);
                assertTrue(prev - curr < .02f);
                prev=curr;
            }
        }
    }

    private static float rimWeight(float incidence,float softness,float extent){
        float grazing = Math.max(0f, Math.min(1f,1f-incidence));
        float reachStart=1f-1.2f*extent;
        float progress=Math.max(0f, Math.min(1f,
                (grazing-reachStart)/Math.max(1f-reachStart,.001f)));
        float quick=smooth(progress/.08f);
        float gradual=smooth(progress);
        float taper=quick*(1f-Math.min(1f,softness*.5f))
                + gradual*Math.min(1f,softness*.5f);
        float e2=extent*extent;
        float e4=e2*e2;
        return Math.max(taper,.18f*e4*e4);
    }

    private static float smooth(float t) {
        t=Math.max(0f,Math.min(1f,t));
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

    @Test public void refractionDrivesBothBrightAndDarkContourRgb() {
        float brown = .49f, gray = .42f, os4White = .98f;
        assertTrue(contourColor(brown, os4White, 0f) > brown);
        assertTrue(contourColor(gray, os4White, 0f) > gray);
        assertTrue(contourColor(brown, os4White, 1f) < brown * .04f);
        assertTrue(contourColor(gray, os4White, 1f) < gray * .04f);
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
        float lit = transmitted*.12f + Math.max(transmitted,highlight)*.88f;
        float dark = transmitted*.035f;
        float shade = smooth(Math.min(1f, Math.max(0f, darkWeight/.72f)));
        return lit*(1f-shade) + dark*shade;
    }
}

