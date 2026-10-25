package com.hellovoid.prismal;

import org.junit.Test;
import org.junit.Assume;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.Assert.*;

public class PrismalContinuousCornersTest {
    @Test public void zeroRadiusIsAnExactRectangle() {
        assertArrayEquals(new float[]{0, 0, 400, 0, 400, 80, 0, 80},
                PrismalContinuousCorners.contour(400, 80, 0), 0f);
    }

    @Test public void fullRadiusSquareIsACircle() {
        float[] points = PrismalContinuousCorners.contour(100, 100, 1000);
        for (int i = 0; i < points.length; i += 2) {
            assertEquals(50, Math.hypot(points[i] - 50, points[i + 1] - 50), .001);
        }
    }

    @Test public void dockShoulderExtendsBeyondItsRadiusAndStaysInBounds() {
        float[] points = PrismalContinuousCorners.contour(400, 80, 40);
        // Full horizontal room extends a 40px radius to 51.572px.
        assertEquals(348.428f, points[0], .001f);
        assertEquals(0, points[1], 0);
        for (int i = 0; i < points.length; i += 2) {
            assertTrue(Float.isFinite(points[i]) && Float.isFinite(points[i + 1]));
            assertTrue(points[i] >= 0 && points[i] <= 400);
            assertTrue(points[i + 1] >= 0 && points[i + 1] <= 80);
        }
    }

    @Test public void tinyAndNearlyRoundShapesRemainConvexAndMirrorSymmetric() {
        for (float[] size : new float[][]{{1,1}, {100.01f,100}, {120,100}, {400,80}}) {
            float[] p = PrismalContinuousCorners.contour(size[0], size[1], 50);
            for (int i = 0; i < p.length; i += 2) {
                int next = (i + 2) % p.length, after = (i + 4) % p.length;
                double cross = (p[next] - p[i]) * (p[after + 1] - p[next + 1])
                        - (p[next + 1] - p[i + 1]) * (p[after] - p[next]);
                assertTrue("convex contour", cross >= -.002);
                boolean mirror = false;
                for (int j = 0; j < p.length; j += 2) {
                    if (Math.abs(p[j] + p[i] - size[0]) < .001
                            && Math.abs(p[j + 1] - p[i + 1]) < .001) mirror = true;
                }
                assertTrue("horizontal mirror", mirror);
            }
        }
    }
    @Test public void continuousGeometryCompilesInAllGlassPrograms() throws Exception {
        try {
            Assume.assumeTrue(new ProcessBuilder("glslangValidator", "--version")
                    .redirectErrorStream(true).start().waitFor() == 0);
        } catch (IOException unavailable) {
            Assume.assumeNoException("glslangValidator is optional on developer machines", unavailable);
        }
        String baseline = PrismalOs4InvariantShader.apply(PrismalComponentGateShader.apply(
                PrismalOpticalEdgeShader.apply(PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
        String decorated = PrismalEdgeDecorationShader.apply(baseline);
        for (String shader : new String[]{baseline, decorated,
                PrismalReconstructed27Shader.apply(decorated)}) {
            Path file = Files.createTempFile("continuous-glass-", ".frag");
            try {
                Files.writeString(file, shader);
                Process validator = new ProcessBuilder("glslangValidator", file.toString())
                        .redirectErrorStream(true).start();
                String output = new String(validator.getInputStream().readAllBytes());
                assertEquals(output, 0, validator.waitFor());
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

}
