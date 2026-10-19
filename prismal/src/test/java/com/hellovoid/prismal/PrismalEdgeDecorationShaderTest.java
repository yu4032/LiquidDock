package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

/** Generated GLSL contracts for two attached, independent optical bands. */
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

    private static float smooth(float t) {
        t=Math.max(0f, Math.min(1f,t));
        return t*t*(3f-2f*t);
    }

    private static float smoother(float t) {
        t=Math.max(0f,Math.min(1f,t));
        return t*t*t*(t*(t*6f-15f)+10f);
    }

    private static float axisAtAngle(int degrees) {
        double r=Math.toRadians(degrees);
        float ax=(float)Math.sin(r),ay=(float)Math.cos(r);
        return ax/(ax+ay);
    }

    private static float sideAtAngle(int degrees, PrismalEdgeDecoration m) {
        return smoother((axisAtAngle(degrees)-m.backlitSideStart())
                /m.backlitSideSpan());
    }

    private static int occurrences(String text,String needle) {
        int count=0;
        for(int start=0;(start=text.indexOf(needle,start))>=0;start+=needle.length())
            count++;
        return count;
    }

    @Test public void orientationSeparatesHorizontalHighlightFromVerticalOutlineDarkness() {
        PrismalEdgeDecoration m=material(.55f,.6f);
        assertEquals(.3475f,m.backlitSideStart(),.001f);
        assertEquals(.37f,m.backlitSideSpan(),.001f);
        assertEquals(0f,sideAtAngle(0,m),.0001f);
        assertEquals(0f,sideAtAngle(20,m),.0001f);
        assertTrue(sideAtAngle(28,m)<.02f);
        assertTrue(sideAtAngle(45,m)>.30f);
        assertTrue(sideAtAngle(45,m)<.65f);
        assertTrue(sideAtAngle(68,m)>.98f);
        assertEquals(1f,sideAtAngle(90,m),.0001f);
        String shader=decorated();
        assertTrue(shader.contains("float sideAxis = ax / max(ax + ay, 0.0001);"));
        assertTrue(shader.contains("float horizontalWeight = 1.0 - sideWeight;"));
        assertTrue(shader.contains("float sideWeight = opticalSmoother01(sideT);"));
    }

    @Test public void orientationRoundCornerIsContinuousAndDirectionOnlySecondary() {
        PrismalEdgeDecoration m=material(.55f,.6f);
        float previous=sideAtAngle(0,m);
        for(int degrees=1;degrees<=90;degrees++){
            float next=sideAtAngle(degrees,m);
            assertTrue("orientation must be monotone",next>=previous-.00001f);
            assertTrue("no one-degree corner jump",next-previous<.10f);
            previous=next;
        }
        assertTrue(sideAtAngle(29,m)-sideAtAngle(28,m)<.005f);
        assertTrue(sideAtAngle(68,m)-sideAtAngle(67,m)<.005f);
        assertTrue(decorated().contains("float signedFacing = dot(normal, u_invariantLightXY);"));
        assertFalse(decorated().contains("abs(dot(normal, u_invariantLightXY))"));
    }

    @Test public void extentAndSoftnessStayIndependentAndZeroDisablesBothBands() {
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

    @Test public void outerContourIsOutsideAndInnerHighlightIsInsideSameSilhouette() {
        String shader=decorated();
        assertTrue(shader.contains("float surfaceDistance = distMask + insetShiftPx;"));
        assertTrue(shader.contains("float outlineAlpha = 0.0;"));
        assertTrue(shader.contains("float innerHighlightAlpha = 0.0;"));
        assertTrue(shader.contains("float outlineEntry = smoothstep("));
        assertTrue(shader.contains("-aa * 0.70, aa * 0.20, surfaceDistance"));
        assertTrue(shader.contains("float outlineExit = 1.0 - smoothstep("));
        assertTrue(shader.contains("float innerBand = smoothstep("));
        assertTrue(shader.contains("u_backlitStrength * innerBand"));
        assertFalse(shader.contains("float inkDistance ="));
        assertFalse(shader.contains("u_backlitViewportInsetPx"));
        assertFalse(shader.contains("float opticalAlpha ="));
        assertFalse(shader.contains("float coreCoverage ="));

        // The exterior line starts slightly before SDF=0 for AA contact,
        // but its peak is outside; no independently shifted second outline.
        float aa=.75f;
        assertEquals(0f,smooth((-aa+aa*.70f)/(aa*.90f)),.00001f);
        float entryAtSilhouette=smooth(.70f/.90f);
        assertTrue(entryAtSilhouette>.85f && entryAtSilhouette<1f);
        assertEquals(1f,smooth((aa*.70f+aa*.70f)/(aa*.90f)),.00001f);

        float inside=-aa, highlightWidth=2f;
        float glint=smooth((inside+highlightWidth+aa*.6f)/(aa*.9f))
                *(1f-smooth((inside+aa*.10f)/(aa*.85f)));
        assertTrue("inner glint is present separately",glint>.99f);
    }

    @Test public void innerHighlightCannotBeReplacedByOutlineColorOrAlpha() {
        String shader=decorated();
        assertTrue(shader.contains("float glassAndGlintAlpha = innerHighlightAlpha"));
        assertTrue(shader.contains("vec3 glassAndGlintPremul ="));
        assertTrue(shader.contains("OPTICAL_SPECULAR_RGB * innerHighlightAlpha"));
        assertTrue(shader.contains("ringShadedGlass * glassAlpha * (1.0 - innerHighlightAlpha)"));
        assertTrue(shader.contains("ringShadedGlass *= 1.0 - ringAmount;"));
        assertTrue(shader.contains("vec3 glassWithOutlinePremul = outlineRgb * outlineAlpha"));
        assertTrue(shader.contains("glassAndGlintPremul * (1.0 - outlineAlpha)"));
        assertFalse(shader.contains("highlightAlpha *= (1.0 - outlineAlpha)"));
        assertFalse(shader.contains("vec3 rimBase = mix(color"));

        // No outline still leaves a distinct inner mirror glint.
        float glass=.7f, glint=.38f, outline=0f;
        float interior=glint+glass*(1f-glint);
        assertEquals(interior,outline+interior*(1f-outline),.000001f);
        // No inner glint does not remove an exterior outline.
        glint=0f;outline=.65f;
        interior=glint+glass*(1f-glint);
        assertTrue(outline+interior*(1f-outline)>interior);
    }


    @Test public void reference27RefractionUsesTwoLensLobesOnOneSourceTap() {
        String shader=decorated();
        assertTrue(shader.contains("float opticalLensArc(float t)"));
        assertTrue(shader.contains("sqrt(max((2.0 - t) * t, 0.0))"));
        assertTrue(shader.contains("float innerBendPx = max(u_lensRefractionPx, 0.0)"));
        assertTrue(shader.contains("float outerBendPx = rimReflectPx * 0.35"));
        assertTrue(shader.contains("float refractionHandoff = smoothstep("));
        assertTrue(shader.contains("mix(innerBendPx, outerBendPx, refractionHandoff)"));
        assertEquals("two curves share one prepared-background fetch",1,
                occurrences(shader,"texture2D(u_backgroundTexture, rimUv).rgb;"));
        assertFalse(shader.contains("texture2D(u_blurredTexture, rimUv)"));
        float aa=.75f, w=4f, innerPx=7f, outerPx=6f;
        // A smooth AA transition should not jump at the true SDF boundary.
        float minus=bendAt(-.001f,aa,w,innerPx,outerPx);
        float plus=bendAt(.001f,aa,w,innerPx,outerPx);
        assertTrue("no inner/outer refraction seam",Math.abs(plus-minus)<.05f);
        assertTrue("distortion falls off from the surface",
                bendAt(0f,aa,w,innerPx,outerPx)>bendAt(3f,aa,w,innerPx,outerPx));
        assertTrue("bounded by physical edge size",
                bendAt(0f,aa,w,innerPx,outerPx)<=Math.max(innerPx,outerPx));
    }

    private static float lensArc(float t) {
        t=Math.max(0f,Math.min(1f,t));
        return (float)Math.sqrt(Math.max((2f-t)*t,0f));
    }

    private static float bendAt(float d,float aa,float width,float inner,float outer) {
        float a=(Math.max(-d,0f)+aa*.15f)/width;
        float b=(Math.max(d,0f)+aa*.15f)/width;
        float innerBend=inner*.14f*(1-lensArc(a));
        float outerBend=outer*.35f*(1-lensArc(b));
        float h=smooth((d+aa*.65f)/(aa*1.30f));
        float band=1f-smooth(Math.abs(d)/width);
        return (innerBend*(1-h)+outerBend*h)*(.35f+.65f*band);
    }

    @Test public void opposingKeyAndFillRemainDistinctFromAxisMaterialShading() {
        String shader=decorated();
        assertTrue(shader.contains("float opticalOpposingFill(float facing"));
        assertTrue(shader.contains("opticalSmoother01((facing - 0.06) / 0.94)"));
        assertTrue(shader.contains("opticalSmoother01((-facing - 0.06) / 0.94)"));
        assertTrue(shader.contains("float keyKnee = key / (1.0 + 0.35 * (1.0 - key))"));
        assertTrue(shader.contains("float fillKnee = fill / (1.0 + 0.35 * (1.0 - fill))"));
        assertTrue(shader.contains("opticalSpec = horizontalWeight"));
        assertTrue(shader.contains("innerBand"));
        assertTrue(shader.contains("float opposingFill = opticalOpposingFill("));
        assertFalse(shader.contains("float direction01 = 0.5 + 0.5 * signedFacing"));
        // On the negative side the counter-light is meaningful even when
        // the optional opposite intensity is set to zero.
        assertTrue(opposingFill(-1f,.8f,0f)>.45f);
        assertTrue(opposingFill(1f,.8f,0f)>.70f);
        assertTrue(opposingFill(0f,.8f,0f)<.05f);
    }

    private static float opposingFill(float n,float ki,float fi) {
        float key=smoother((n-.06f)/.94f);
        float fill=smoother((-n-.06f)/.94f);
        float ka=key/(1f+.35f*(1f-key));
        float fa=fill/(1f+.35f*(1f-fill));
        return Math.max(0f,Math.min(1f,
                ka*(.72f+.24f*Math.max(0f,Math.min(1f,ki)))
                +fa*(.66f+.20f*Math.max(0f,Math.min(1f,fi)))));
    }

    @Test public void sharpRimUsesSourceTextureWithoutASecondGlassPass() {
        String shader=decorated();
        assertEquals(1,occurrences(shader,"texture2D(u_backgroundTexture, rimUv).rgb;"));
        assertEquals(0,occurrences(shader,"texture2D(u_blurredTexture, rimUv)"));
        assertTrue(shader.contains("if (outlineAlpha > 0.001) {"));
        assertTrue(shader.contains("float opticalMeniscusProfile = 1.0 - smoothstep("));
        assertTrue(shader.contains("0.0, meniscusWidthPx, abs(surfaceDistance)"));
        assertTrue(shader.contains("min(u_glassSize.x, u_glassSize.y) * 0.025"));
        assertTrue(shader.contains("texture2D(u_blurredTexture, uvCenter)"));
        // Never replace original OS4-highlighted body 'color'.
        assertFalse(shader.contains("innerGlassRgb *= "));
    }

    @Test public void darkOutlineKeepsSourceHueAndHorizontalSurfaceReflects() {
        String shader=decorated();
        assertTrue(shader.contains("float darkFloor = mix(0.46, 0.28,"));
        assertTrue(shader.contains("float reflection = (0.10 + 0.70 * specEnergy)"));
        assertFalse(shader.contains("vec3(0.015)"));
        float[] brown={.65f,.38f,.20f};
        float[] bright=new float[3],dark=new float[3];
        for(int i=0;i<3;i++){
            bright[i]=shade(brown[i],.99f,0f,.8f,.43f);
            dark[i]=shade(brown[i],.99f,1f,0f,.43f);
        }
        assertTrue("horizontal must reflect light",bright[0]>.78f);
        assertTrue("dark material retains hue",dark[0]>dark[1]&&dark[1]>dark[2]);
        assertTrue("dark material still attenuates",dark[0]<brown[0]*.50f);
        assertTrue("no neutral black paint",Math.abs(dark[0]/dark[2]-brown[0]/brown[2])<.001f);
    }

    private static float shade(float rgb,float spec,float dark,float energy,float y) {
        float floor=.46f+(.28f-.46f)*smooth((y-.18f)/(.72f-.18f));
        float trans=1f+(floor-1f)*dark;
        float reflection=(.10f+.70f*energy)*(1f-dark);
        return rgb*trans*(1f-reflection)+spec*reflection;
    }


    @Test public void macOs27ErfcRingDifferenceProducesBoundedSoftDarkBand() {
        String fragment=decorated();
        assertTrue(fragment.contains("float opticalErfcHalf(float distance)"));
        assertTrue(fragment.contains("p = p * x2 - 0.560550;"));
        assertTrue(fragment.contains("float ringInner = opticalErfcHalf("));
        assertTrue(fragment.contains("float ringOuter = opticalErfcHalf("));
        assertTrue(fragment.contains("(ringInner - ringOuter) * 0.58"));
        assertTrue(fragment.contains("ringShadedGlass *= 1.0 - ringAmount;"));
        assertTrue(fragment.contains("float invRing = 0.70710678 / ringRadiusPx;"));
        assertTrue(fragment.contains("ringDepth < ringRangePx"));
        assertEquals(.5f,erfcHalf(0f),.00001f);
        assertTrue(erfcHalf(-1f)>.5f);
        assertTrue(erfcHalf(1f)<.5f);
        assertEquals(1f,erfcHalf(-20f),.002f);
        assertEquals(0f,erfcHalf(20f),.002f);
        float previous=Float.POSITIVE_INFINITY;
        for (int d=0;d<=8;d++) {
            float r=ring(d,2f,1.5f);
            assertTrue("ring>=0",r>=0f);
            assertTrue("ring<=1",r<=1f);
            assertTrue("ring fades inward",r<=previous+.002f);
            previous=r;
        }
        assertTrue(ring(0f,2f,1.5f)>.10f);
        assertTrue(ring(8f,2f,1.5f)<.01f);
    }

    private static float erfcHalf(float distance) {
        float u=Math.max(0f,Math.min(1f,.25f*distance+.5f));
        float x=4f*u-2f,x2=x*x,p=.002954f;
        p=p*x2-.034460f;
        p=p*x2+.168210f;
        p=p*x2-.560550f;
        return Math.max(0f,Math.min(1f,p*x+.5f));
    }

    private static float ring(float depth,float radius,float stroke) {
        return Math.max(0f,Math.min(1f,
                erfcHalf(depth*.70710678f/radius)
                        -erfcHalf((depth+stroke)*.70710678f/radius)));
    }

    @Test public void macOs27HoldingToneAndRingAreScopedToInsideGlass() {
        String shader=decorated();
        int begin=shader.indexOf("vec3 ringShadedGlass = color;");
        int end=shader.indexOf("float glassAlpha = opacity * u_transmittance;",begin);
        assertTrue(begin>=0&&end>begin);
        String ring=shader.substring(begin,end);
        assertTrue(ring.contains("surfaceDistance < 0.0"));
        assertTrue(ring.contains("float hold = opticalErfcHalf("));
        assertTrue(ring.contains("ringDepth - ringStrokePx * 1.5"));
        assertTrue(ring.contains("vec3(0.212646, 0.715332, 0.072205)"));
        assertTrue(ring.contains("ringShadedGlass = mix(ringShadedGlass, vec3(luma),"));
        assertFalse("no extra texture read for ring or hold",ring.contains("texture2D("));
        assertTrue(shader.contains("u_backlitSideStart < 1.0"));
    }

    @Test public void postCoverageGlintUsesExponentialWithoutPaintingExterior() {
        String shader=decorated();
        int blend=shader.indexOf("float finalAlpha = glassWithOutlineAlpha");
        int glint=shader.indexOf("float rimGlint = exp(-abs(surfaceDistance)");
        int output=shader.indexOf("gl_FragColor = vec4(finalRgb, finalAlpha);",glint);
        assertTrue(blend>0&&glint>blend&&output>glint);
        assertTrue(shader.contains("0.10 * u_backlitStrength * rimGlint"));
        assertTrue(shader.contains("* (1.0 - opticalDark) * (1.0 - outlineAlpha)"));
        assertTrue(shader.contains("/ max(finalAlpha, 0.25)"));
        assertTrue(shader.contains("innerHighlightAlpha > 0.001"));
        assertTrue(shader.contains("surfaceDistance < 0.0"));
        double near=Math.exp(-.3/1.5),far=Math.exp(-3/1.5);
        assertTrue(near>far*3.0);
        // Original blurred body and one rim sharp tap remain unchanged.
        assertEquals(1,occurrences(shader,"texture2D(u_backgroundTexture, rimUv).rgb;"));
        assertTrue(shader.contains("texture2D(u_blurredTexture, uvCenter)"));
    }

    @Test public void shadowOnlyBranchKeepsBaseGlassUnpainted() {
        PrismalEdgeDecoration shadow=new PrismalEdgeDecoration(
                0f,0f,.6f,15f,4f,0f,0f,0f,.2f,.5f);
        assertFalse(shadow.hasBacklit());
        assertTrue(shadow.hasShadow());
        assertTrue(shadow.extraGuardPx(2f)>0f);
        assertFalse(shadow.withoutCastShadow().hasShadow());
        String shader=decorated();
        assertTrue(shader.contains("if (surfaceDistance > 0.0 && u_castShadowRadiusPx > 0.0"));
        assertTrue(shader.contains("if (opacity < 0.001) {"));
        assertTrue(shader.contains("exteriorShadowAlpha * (1.0 - outlineAlpha)"));
        assertTrue(shader.contains("float glassAlpha = opacity * u_transmittance;"));
    }

    @Test public void layerOrderIsGlassThenHighlightThenOutlineThenShadow() {
        String shader=decorated();
        int glass=shader.indexOf("float glassAlpha = opacity * u_transmittance;");
        int highlight=shader.indexOf("float glassAndGlintAlpha = innerHighlightAlpha");
        int outline=shader.indexOf("float glassWithOutlineAlpha = outlineAlpha");
        int shadow=shader.indexOf("float finalAlpha = glassWithOutlineAlpha");
        assertTrue(glass>0&&highlight>glass&&outline>highlight&&shadow>outline);
        assertTrue(shader.contains("float finalAlpha = glassWithOutlineAlpha"));
        assertTrue(shader.contains("vec3 finalPremul = glassWithOutlinePremul"));
        assertTrue(shader.contains("vec3 finalRgb = finalPremul / max(finalAlpha, 0.0001);"));
        assertTrue(shader.contains("gl_FragColor = vec4(finalRgb, finalAlpha);"));
    }

    @Test public void fragmentUsesNoLaterOs4LocalBeforeEarlyDiscard() {
        String shader=decorated();
        int start=shader.indexOf("float surfaceDistance = distMask + insetShiftPx;");
        int early=shader.indexOf("if (opacity < 0.001) {",start);
        int late=shader.indexOf("float os4SoftLight =");
        assertTrue(start>=0&&early>start&&late>early);
        String beforeDiscard=shader.substring(start,early);
        assertFalse(beforeDiscard.contains("os4SoftLight"));
        assertFalse(beforeDiscard.contains("hiSoft"));
        assertTrue(beforeDiscard.contains("float opposingFill = opticalOpposingFill("));
        assertFalse(beforeDiscard.contains("os4SoftLight"));
        assertFalse(beforeDiscard.contains("directionalFill"));
        assertTrue(beforeDiscard.contains("float signedFacing = dot(normal, u_invariantLightXY);"));
    }

    @Test public void baselineAndNativeDockGeometryRemainUnchanged() {
        assertFalse(PrismalEdgeDecoration.NONE.hasBacklit());
        assertFalse(PrismalEdgeDecoration.NONE.hasShadow());
        assertEquals(0f,PrismalEdgeDecoration.NONE.extraGuardPx(3f),.001f);
        PrismalEdgeDecoration m=material(.55f,.6f);
        PrismalEdgeDecoration dock=m.fitInkToNativeViewport(1f);
        assertEquals(1f,dock.inkViewportInsetPx,.00001f);
        assertEquals(m.backlitWidthDp,dock.backlitWidthDp,.00001f);
        assertFalse(decorated().contains("u_backlitViewportInsetPx"));
        assertSame(PrismalEdgeDecoration.NONE,
                PrismalEdgeDecoration.NONE.fitInkToNativeViewport(1.5f));
        String vertex=PrismalRasterGuardShader.applyWithEdgeGuard(
                PrismalShaderSources.VERTEX);
        assertTrue(vertex.contains("u_edgeRasterGuardPx"));
    }
}
