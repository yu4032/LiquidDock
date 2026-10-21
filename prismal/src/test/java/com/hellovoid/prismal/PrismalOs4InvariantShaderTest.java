package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

public class PrismalOs4InvariantShaderTest {
    @Test public void numericValuesMatchOriginalExpressions() {
        for (float x : new float[]{-2f, -0.5f, 0f, 0.77f, 2.3f}) {
            for (float y : new float[]{-1.8f, -0.8f, 0f, 0.65f, 2.1f}) {
                for (float ior : new float[]{1.01f, 1.15f, 1.5f, 2f, 4f}) {
                    PrismalParams.Builder builder = PrismalParams.builder();
                    builder.lightDirX = x;
                    builder.lightDirY = y;
                    builder.ior = ior;
                    builder.shadowSoftness = 7.5f;
                    PrismalParams p = builder.build();
                    float[] out = new float[10];
                    PrismalOs4InvariantShader.compute(p, out);

                    assertVector(normal3(x, y, 1.45), out, 0);
                    double[] lxy = normal2(x + 1e-5, y + 1e-5);
                    assertEquals(lxy[0], out[3], 0.000001);
                    assertEquals(lxy[1], out[4], 0.000001);
                    assertVector(normal3(lxy[0], lxy[1], 1.0), out, 5);

                    double ratio = (1.0 - ior) / (1.0 + ior);
                    assertEquals(ratio * ratio, out[8], 0.000001);
                    assertEquals(0.15 + 0.45 * (7.5 / 20.0), out[9], 0.000001);
                }
            }
        }
    }

    @Test public void shadowExtentClampsLikeOpticalEdgeShader() {
        float[] out = new float[10];
        PrismalParams.Builder low = PrismalParams.builder();
        low.shadowSoftness = -100f;
        PrismalOs4InvariantShader.compute(low.build(), out);
        assertEquals(0.15f, out[9], 0.000001f);
        PrismalParams.Builder high = PrismalParams.builder();
        high.shadowSoftness = 100f;
        PrismalOs4InvariantShader.compute(high.build(), out);
        assertEquals(0.60f, out[9], 0.000001f);
    }

    private static double[] normal2(double x, double y) {
        double len = Math.sqrt(x*x + y*y);
        return len <= 0 ? new double[]{0,0} : new double[]{x/len,y/len};
    }

    private static double[] normal3(double x, double y, double z) {
        double len = Math.sqrt(x*x + y*y + z*z);
        return new double[]{x/len,y/len,z/len};
    }

    private static void assertVector(double[] want, float[] actual, int start) {
        for (int i=0;i<3;i++) assertEquals(want[i], actual[start+i], 0.000001);
    }
}
