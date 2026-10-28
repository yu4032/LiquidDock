package com.hellovoid.prismal;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class PrismalInteriorFastPathTest {
    @Test public void flatLegacyInteriorSkipsFourHeightSamplesButContinuousCornersRetainThem() {
        String shader = PrismalInteriorFastPath.apply(PrismalOpticalEdgeShader.apply(
                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
        assertTrue(shader.contains("u_continuousCorners > 0.5 || edgeDist < tw + 4.0"));
        assertTrue(shader.contains("gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw)"));
    }

    @Test public void backgroundReflectionIsSampledOnlyWhenItHasWeight() {
        String shader = PrismalInteriorFastPath.apply(PrismalOpticalEdgeShader.apply(
                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
        assertTrue(shader.contains("if (reflW > 0.0)"));
        assertTrue(shader.contains("color = mix(color, reflSample, reflW);"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsChangedShaderRatherThanSilentlySkippingOptimization() {
        PrismalInteriorFastPath.apply("not a supported shader");
    }
    @Test public void legacyFieldSafetyMarginKeepsAllFourHeightSamplesFlat() {
        Random random = new Random(0x05f04);
        int checked = 0;
        for (int i = 0; i < 200000; i++) {
            double halfWidth = 8 + random.nextDouble() * 500;
            double halfHeight = 8 + random.nextDouble() * 500;
            double radius = random.nextDouble() * Math.min(halfWidth, halfHeight);
            double smooth = random.nextDouble() * 24;
            double transition = 1 + random.nextDouble() * 90;
            double x = (random.nextDouble() * 2 - 1) * halfWidth;
            double y = (random.nextDouble() * 2 - 1) * halfHeight;
            if (-field(x,y,halfWidth,halfHeight,radius,smooth) < transition + 4) continue;
            checked++;
            assertTrue(field(x+1,y,halfWidth,halfHeight,radius,smooth) <= -transition);
            assertTrue(field(x-1,y,halfWidth,halfHeight,radius,smooth) <= -transition);
            assertTrue(field(x,y+1,halfWidth,halfHeight,radius,smooth) <= -transition);
            assertTrue(field(x,y-1,halfWidth,halfHeight,radius,smooth) <= -transition);
        }
        assertTrue("insufficient interior samples", checked > 10000);
    }

    private static double field(double x, double y, double bx, double by, double r, double k) {
        double qx = Math.abs(x) - bx + r, qy = Math.abs(y) - by + r;
        if (k <= 0) return Math.min(Math.max(qx,qy),0) + Math.hypot(Math.max(qx,0),Math.max(qy,0))-r;
        double outer = smax(qx,qy,k);
        return smin(outer,0,k*.5) + Math.hypot(smax(qx,0,k),smax(qy,0,k))-r;
    }
    private static double smax(double a, double b, double k) {
        double h = Math.max(0,Math.min(1,.5+.5*(a-b)/k));
        return b*(1-h)+a*h+k*h*(1-h);
    }
    private static double smin(double a, double b, double k) {
        double h = Math.max(0,Math.min(1,.5+.5*(b-a)/k));
        return b*(1-h)+a*h-k*h*(1-h);
    }

}
