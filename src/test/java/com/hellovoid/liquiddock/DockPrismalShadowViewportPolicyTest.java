package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure geometry and shader-output contracts, independent of Android Views. */
public final class DockPrismalShadowViewportPolicyTest {
    private static PrismalEdgeDecoration material(boolean shadow) {
        return new PrismalEdgeDecoration(
                1f, 0.8f, .6f,
                shadow ? 8f : 0f,
                2f, 0f, 0f, 0f,
                shadow ? .5f : 0f,
                .4f, .55f);
    }

    @Test public void glassOnlyShadowNeedsAnOuterOutputViewport() {
        int px = DockPrismalShadowViewportPolicy.marginPx(
                material(true), 3f, 170);
        assertTrue("GPU shadow pixels must fit outside native Dock",
                px >= 12 && px <= 96);
        assertEquals(0, DockPrismalShadowViewportPolicy.marginPx(
                material(false), 3f, 170));
        assertEquals(0, DockPrismalShadowViewportPolicy.marginPx(
                material(true), 3f, 0));
        assertEquals(0, DockPrismalShadowViewportPolicy.marginPx(null, 3f, 170));
    }

    @Test public void largerOutputPreservesNativeDockSizeAndRelativeLocation() {
        int pad=35, inset=12, bodyW=440, bodyH=132;
        int outputW=bodyW+2*pad, outputH=bodyH+2*pad;
        int sampleW=outputW+2*inset, sampleH=outputH+2*inset;

        float bodyLeft=DockPrismalShadowViewportPolicy.normalizedBodyX(
                inset,pad,sampleW);
        float bodyBottom=DockPrismalShadowViewportPolicy.normalizedBodyBottom(
                inset,outputH,pad,bodyH,sampleH);
        float bodyU=bodyW/(float)sampleW;
        float bodyV=bodyH/(float)sampleH;
        float outputLeft=DockPrismalShadowViewportPolicy.normalizedOutputOrigin(
                inset,sampleW);
        float outputBottom=DockPrismalShadowViewportPolicy.normalizedOutputOrigin(
                inset,sampleH);
        float outputU=DockPrismalShadowViewportPolicy.normalizedOutputExtent(
                outputW,sampleW);
        float outputV=DockPrismalShadowViewportPolicy.normalizedOutputExtent(
                outputH,sampleH);

        assertTrue(bodyLeft>outputLeft);
        assertTrue(bodyBottom>outputBottom);
        assertEquals(pad,Math.round((bodyLeft-outputLeft)*sampleW));
        assertEquals(pad,Math.round((bodyBottom-outputBottom)*sampleH));
        assertEquals(bodyW,Math.round(bodyU*sampleW));
        assertEquals(bodyH,Math.round(bodyV*sampleH));
        assertEquals(outputW,Math.round(outputU*sampleW));
        assertEquals(outputH,Math.round(outputV*sampleH));
    }

    @Test public void zeroOverscanGivesOriginalNativeExactCrop() {
        int width=500,height=160,insetX=24,insetY=12;
        int sw=width+2*insetX,sh=height+2*insetY;
        assertEquals(
                DockPrismalShadowViewportPolicy.normalizedOutputOrigin(insetX,sw),
                DockPrismalShadowViewportPolicy.normalizedBodyX(insetX,0,sw),0f);
        assertEquals(
                DockPrismalShadowViewportPolicy.normalizedOutputOrigin(insetY,sh),
                DockPrismalShadowViewportPolicy.normalizedBodyBottom(
                        insetY,height,0,height,sh),0f);
        assertEquals(width/(float)sw,
                DockPrismalShadowViewportPolicy.normalizedOutputExtent(width,sw),0f);
    }

    @Test public void shadowGuardIsClampedForHighDensityAndLargeNodes() {
        for (int density=1;density<=5;density++) {
            for (int shortSide=1;shortSide<=640;shortSide+=17) {
                int px=DockPrismalShadowViewportPolicy.marginPx(
                        material(true),density,shortSide);
                assertTrue(px>=0 && px<=96);
            }
        }
    }
}
