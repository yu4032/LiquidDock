package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The optical meniscus is a single, centered SDF material response.
 * It must not act like an independently positioned outer ink stroke.
 */
public class PrismalEdgeDecorationShaderTest {
    private static String upstream() {
        return PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    }

    private static String decorated() {
        return PrismalEdgeDecorationShader.apply(upstream());
    }

    private static PrismalEdgeDecoration material(float extent, float softness) {
        return new PrismalEdgeDecoration(
                1f, .85f, softness, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, extent);
    }

    private static float smoother(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t*t*t*(t*(t*6f-15f)+10f);
    }

    private static float axisAtAngle(int degrees) {
        double r = Math.toRadians(degrees);
        float ax = (float) Math.sin(r), ay = (float) Math.cos(r);
        return ax/(ax+ay);
    }

    private static float sideAtAngle(int degrees, PrismalEdgeDecoration m) {
        return smoother((axisAtAngle(degrees)-m.backlitSideStart())
                /m.backlitSideSpan());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int start=0; (start=text.indexOf(needle,start))>=0; start+=needle.length())
            count++;
        return count;
    }

    @Test public void orientationSeparatesHorizontalSpecularFromVerticalDarkness() {
        PrismalEdgeDecoration m=material(.55f,.6f);
        assertEquals(.3475f,m.backlitSideStart(),.001f);
        assertEquals(.37f,m.backlitSideSpan(),.001f);
        assertEquals(0f,sideAtAngle(0,m),.0001f);
        assertEquals(0f,sideAtAngle(20,m),.0001f);
        assertTrue(sideAtAngle(28,m)<.02f);
        assertTrue(sideAtAngle(45,m)>.35f);
        assertTrue(sideAtAngle(45,m)<.65f);
        assertTrue(sideAtAngle(68,m)>.98f);
        assertEquals(1f,sideAtAngle(90,m),.0001f);
        assertTrue(decorated().contains("float sideAxis = ax / max(ax + ay, 0.0001);"));
        assertTrue(decorated().contains("float horizontalWeight = 1.0 - sideWeight;"));
        assertTrue(decorated().contains("float sideWeight = opticalSmoother01(sideT);"));
    }

    @Test public void orientationRoundCornerHasContinuousFirstDerivative() {
        PrismalEdgeDecoration m=material(.55f,.6f);
        float previous=sideAtAngle(0,m);
        for(int degrees=1;degrees<=90;degrees++){
            float next=sideAtAngle(degrees,m);
            assertTrue("orientation must be monotone",next>=previous-.00001f);
            assertTrue("one degree must not jump",next-previous<.10f);
            previous=next;
        }
        assertTrue(sideAtAngle(29,m)-sideAtAngle(28,m)<.005f);
        assertTrue(sideAtAngle(68,m)-sideAtAngle(67,m)<.005f);
        // Top/bottom and left/right response is rotationally symmetric;
        // signed OS4 direction is a secondary non-mirrored modulation.
        assertTrue(decorated().contains("float signedFacing = dot(normal, u_invariantLightXY);"));
        assertFalse(decorated().contains("abs(dot(normal, u_invariantLightXY))"));
    }

    @Test public void extentAndSoftnessRemainIndependentAndZeroDisablesRim() {
        float previousStart=Float.POSITIVE_INFINITY;
        for(float e:new float[]{.001f,.1f,.25f,.55f,.8f,1f}){
            PrismalEdgeDecoration a=material(e,.05f),b=material(e,2f);
            assertTrue(a.backlitSideStart()<=previousStart);
            assertEquals(a.backlitSideStart(),b.backlitSideStart(),.00001f);
            assertTrue(b.backlitSideSpan()>a.backlitSideSpan());
            previousStart=a.backlitSideStart();
        }
        PrismalEdgeDecoration zero=material(0f,.6f);
        assertFalse(zero.hasBacklit());
        assertTrue(zero.backlitSideStart()>1f);
        assertTrue(decorated().contains("u_backlitSideStart < 1.0"));
        assertFalse(decorated().contains("u_backlitGrazingStart"));
    }

    @Test public void silhouetteCoreHasOneContinuousCoverageAcrossZero() {
        String s=decorated();
        assertTrue(s.contains("float surfaceDistance = distMask + insetShiftPx;"));
        assertTrue(s.contains("coreHalfPx, coreHalfPx + aa, abs(surfaceDistance)"));
        assertTrue(s.contains("float opticalAlpha = 0.0;"));
        assertTrue(s.contains("float coreAndGlassAlpha = opticalAlpha"));
        assertTrue(s.contains("vec3 coreAndGlassPremul = rimRgb * opticalAlpha"));
        assertTrue(s.contains("float finalAlpha = coreAndGlassAlpha"));
        assertFalse(s.contains("float inkDistance ="));
        assertFalse(s.contains("u_backlitViewportInsetPx"));
        assertFalse(s.contains("exteriorInkAlpha"));
        assertFalse(s.contains("innerGlint"));
        assertFalse(s.contains("smoothstep(0.55, 1.0, opacity)"));
        // At the real silhouette both halves share the same coverage.
        float widthPx=3f, aa=.75f, half=Math.max(.55f,widthPx*.28f);
        float center=1f-smooth((0f-half)/aa);
        float a=.01f;
        float left=1f-smooth((a-half)/aa),right=1f-smooth((a-half)/aa);
        assertEquals(1f,center,.000001f);
        assertEquals(left,right,.000001f);
        float defaultAlpha=center*(.92f+.07f*.85f);
        assertTrue("lit core must overcome half-transparent backdrop",defaultAlpha>.97f);
    }

    private static float smooth(float t) {
        t=Math.max(0f,Math.min(1f,t));
        return t*t*(3f-2f*t);
    }

    @Test public void shadowOnlyIsStillSupportedWithoutOpticalPainting() {
        PrismalEdgeDecoration shadow=new PrismalEdgeDecoration(
                0f,0f,.6f,15f,4f,0f,0f,0f,.2f,.5f);
        assertFalse(shadow.hasBacklit());
        assertTrue(shadow.hasShadow());
        assertTrue(shadow.extraGuardPx(2f)>0f);
        assertFalse(shadow.withoutCastShadow().hasShadow());
        String s=decorated();
        assertTrue(s.contains("if (surfaceDistance > 0.0 && u_castShadowRadiusPx > 0.0"));
        assertTrue(s.contains("if (opacity < 0.001) {"));
        assertTrue(s.contains("exteriorShadowAlpha * (1.0 - opticalAlpha)"));
    }

    @Test public void opticalRimRetainsSharpPreparedSourceAndDistanceHandoff() {
        String s=decorated();
        assertEquals(1,occurrences(s,"texture2D(u_backgroundTexture, rimUv).rgb;"));
        assertEquals(0,occurrences(s,"texture2D(u_blurredTexture, rimUv)"));
        assertTrue(s.contains("float sharpEndPx = clamp(aa * 4.0, 2.75, 4.5);"));
        assertTrue(s.contains("1.25, sharpEndPx, insidePx"));
        assertTrue(s.contains("vec3 rimBase = mix(color, edgeTransmission, sharpWeight);"));
        assertTrue(s.contains("sharpWeight * 0.16"));
        assertTrue(s.contains("texture2D(u_blurredTexture, uvCenter)"));
        assertTrue(s.contains("float meniscus = 1.0 - smoothstep("));
        assertTrue(s.contains("0.0, meniscusWidthPx, abs(surfaceDistance)"));
        assertFalse(s.contains("rimBendPx = min(12.0"));
        assertTrue(s.contains("float sizeCapPx = clamp(minDim * 0.025, 3.0, 9.0);"));
    }

    @Test public void darkRimKeepsSourceHueAndLitRimReflectsWithoutBlackInk() {
        String s=decorated();
        assertTrue(s.contains("float darkFloor = mix(0.46, 0.28,"));
        assertTrue(s.contains("float reflection = (0.10 + 0.70 * specEnergy)"));
        assertFalse(s.contains("vec3(0.015)"));
        float[] brown={.65f,.38f,.20f};
        float[] bright=new float[3], dark=new float[3];
        for(int i=0;i<3;i++){
            bright[i]=shade(brown[i],.99f,0f,.8f,.43f);
            dark[i]=shade(brown[i],.99f,1f,0f,.43f);
        }
        assertTrue("top must be a visible specular",bright[0]>.78f);
        assertTrue("dark brown edge retains hue",dark[0]>dark[1]&&dark[1]>dark[2]);
        assertTrue("dark edge should be visibly attenuated",dark[0]<brown[0]*.50f);
        assertTrue("source hue retained",Math.abs(dark[0]/dark[2]-brown[0]/brown[2])<.001f);
    }

    private static float shade(float rgb,float spec,float dark,float energy,float y) {
        float floor=.46f+(.28f-.46f)*smooth((y-.18f)/(.72f-.18f));
        float trans=1f+(floor-1f)*dark;
        float reflection=(.10f+.70f*energy)*(1f-dark);
        return rgb*trans*(1f-reflection)+spec*reflection;
    }

    @Test public void innerAttenuationIsShallowAndNotAnotherStroke() {
        String s=decorated();
        assertTrue(s.contains("0.10 * opticalDark * innerShade"));
        assertTrue(s.contains("smoothstep(0.0, 0.65, insidePx)"));
        assertTrue(s.contains("smoothstep(1.5, 3.5, insidePx)"));
        assertFalse(s.contains("color = mix(color, OPTICAL_SPECULAR_RGB, innerGlint)"));
    }

    @Test public void finalBlendIsStraightAlphaAndMaterialOverShadow() {
        String s=decorated();
        assertTrue(s.contains("coreAndGlassPremul / max(")==false);
        assertTrue(s.contains("finalPremul / max(finalAlpha, 0.0001)"));
        assertTrue(s.contains("u_castShadowColor.rgb * exteriorShadowAlpha"));
        assertTrue(s.contains("glassAlpha * (1.0 - opticalAlpha)"));
        assertFalse(s.contains("gl_FragColor = vec4(underlayRgb, underlayAlpha)"));
        assertFalse(s.contains("if (inkDistance > 0.0)"));
    }

    @Test public void legacyHighlightScopeAndProgramFallbackRemainSafe() {
        String s=decorated();
        assertTrue(s.contains("const vec3 OPTICAL_SPECULAR_RGB = vec3("));
        assertFalse(s.contains("vec3 hiSoft =") && s.contains("vec3 rim = refractedContourColor("));
        int a=s.indexOf("vec3 shadeOpticalMeniscus(");
        int b=s.indexOf("return clamp(transmitted * transmission",a);
        assertTrue(a>0&&b>a);
        String helper=s.substring(a,b);
        assertFalse(helper.contains("u_os4DirectionalIntensity"));
        assertFalse(helper.contains("u_os4DirectionalOppositeIntensity"));
        assertFalse(helper.contains("os4SoftLight"));
        assertTrue(s.contains("uniform float u_backlitSideStart;"));
        assertTrue(s.contains("uniform float u_backlitSideSpan;"));
    }

    @Test public void unmodifiedBaselineAndNativeDockGeometryArePreserved() {
        assertFalse(PrismalEdgeDecoration.NONE.hasBacklit());
        assertFalse(PrismalEdgeDecoration.NONE.hasShadow());
        assertEquals(0f,PrismalEdgeDecoration.NONE.extraGuardPx(3f),.001f);
        PrismalEdgeDecoration m=material(.55f,.6f);
        PrismalEdgeDecoration dock=m.fitInkToNativeViewport(1f);
        assertEquals(1f,dock.inkViewportInsetPx,.00001f);
        assertEquals(m.backlitWidthDp,dock.backlitWidthDp,.00001f);
        // The historical inset metadata remains for callers, but it cannot
        // move the SDF optical response away from the native glass mask.
        assertFalse(decorated().contains("u_backlitViewportInsetPx"));
        assertSame(PrismalEdgeDecoration.NONE,
                PrismalEdgeDecoration.NONE.fitInkToNativeViewport(1.5f));
        String vertex=PrismalRasterGuardShader.applyWithEdgeGuard(
                PrismalShaderSources.VERTEX);
        assertTrue(vertex.contains("u_edgeRasterGuardPx"));
    }
}
