package com.hellovoid.prismal;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class PrismalInteriorFastPathTest {
    @Test public void flatInteriorSkipsSamplesWithSeparateContinuousSafetyBound() {
        String shader = PrismalInteriorFastPath.apply(PrismalOpticalEdgeShader.apply(
                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
        assertTrue(shader.contains("if (!continuousFlat)"));
        assertTrue(shader.contains("tw + sampleStep + 0.01"));
        assertTrue(shader.contains("gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw)"));
    }

    @Test public void continuousFlatBoundKeepsAllFourOriginalSamplesSaturated() {
        Random random = new Random(0x484);
        int checked = 0;
        for (int i = 0; i < 200000; i++) {
            double bx = 8 + random.nextDouble() * 600, by = 8 + random.nextDouble() * 600;
            double radius = random.nextDouble() * Math.min(bx, by);
            double step = 1 + random.nextDouble() * 32, tw = 1 + random.nextDouble() * 90;
            double x = (random.nextDouble() * 2 - 1) * bx;
            double y = (random.nextDouble() * 2 - 1) * by;
            if (Math.min(bx - Math.abs(x), by - Math.abs(y)) < tw + step + .01
                    || Math.min(Math.abs(x) + step - (bx - Math.min(1.29 * radius, bx)),
                        Math.abs(y) + step - (by - Math.min(1.29 * radius, by))) > 0) continue;
            checked++;
            // Even different corner radii at the four probes are covered by the maximum radius.
            assertTrue(continuousField(x+step,y,bx,by,radius*random.nextDouble()) <= -tw);
            assertTrue(continuousField(x-step,y,bx,by,radius*random.nextDouble()) <= -tw);
            assertTrue(continuousField(x,y+step,bx,by,radius*random.nextDouble()) <= -tw);
            assertTrue(continuousField(x,y-step,bx,by,radius*random.nextDouble()) <= -tw);
        }
        assertTrue("insufficient flat continuous samples", checked > 10000);
    }

    private static double continuousField(double x, double y, double bx, double by, double r) {
        double ex = Math.abs(x)-bx, ey = Math.abs(y)-by;
        double box = Math.min(Math.max(ex,ey),0)+Math.hypot(Math.max(ex,0),Math.max(ey,0));
        if (r <= .001) return box;
        double roomX = Math.max(0,Math.min(1,(bx-r)/Math.max(r,1e-6)));
        double roomY = Math.max(0,Math.min(1,(by-r)/Math.max(r,1e-6)));
        double reachX = r*Math.min(.2893*Math.pow(roomX,.6),roomX);
        double reachY = r*Math.min(.2893*Math.pow(roomY,.6),roomY);
        double rx = Math.min(r+reachX,bx), ry = Math.min(r+reachY,by);
        double qx = Math.abs(x)-(bx-rx), qy = Math.abs(y)-(by-ry);
        if (Math.min(qx,qy) <= 0) return box;
        double nx = 2+.7198*reachX/Math.max(.2893*r,1e-6);
        double ny = 2+.7198*reachY/Math.max(.2893*r,1e-6);
        double sx = Math.pow(qx/rx,nx), sy = Math.pow(qy/ry,ny), total = sx+sy;
        if (total <= 1e-20) return box;
        double wx = sx/total, wy = sy/total;
        return Math.max(box,(rx*wx+ry*wy)*(Math.pow(total,1/(nx*wx+ny*wy))-1));
    }

    @Test public void backgroundReflectionIsSampledOnlyWhenItHasWeight() {
        String shader = PrismalInteriorFastPath.apply(PrismalOpticalEdgeShader.apply(
                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)));
        assertTrue(shader.contains("if (reflW > 0.0)"));
        assertTrue(shader.contains("color = mix(color, reflSample, reflW"));
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
