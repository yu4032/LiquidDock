package com.hellovoid.prismal;

import static org.junit.Assert.*;
import java.util.Random;
import org.junit.Test;

/** CPU golden check of Gaussian normalization and GL_LINEAR tap-pair equivalence. */
public class PrismalGaussianBlurKernelTest {
    @Test public void tapCountsAndShaderUniformsStayGles2Compatible() {
        assertEquals(31, occurrences(PrismalGaussianBlurKernel.HORIZONTAL_FRAGMENT, "texture2D("));
        assertEquals(17, occurrences(PrismalGaussianBlurKernel.VERTICAL_FRAGMENT, "texture2D("));
        assertTrue(PrismalGaussianBlurKernel.HORIZONTAL_FRAGMENT.contains("u_weights[15]"));
        assertTrue(PrismalGaussianBlurKernel.VERTICAL_FRAGMENT.contains("u_pairs[6]"));
        assertTrue(PrismalGaussianBlurKernel.VERTICAL_FRAGMENT.contains("u_texelSize.y"));
        assertFalse(PrismalGaussianBlurKernel.HORIZONTAL_FRAGMENT.contains("u_pairs"));
        assertFalse(PrismalGaussianBlurKernel.VERTICAL_FRAGMENT.contains("exp("));
        assertFalse(PrismalGaussianBlurKernel.HORIZONTAL_FRAGMENT.contains("exp("));
        assertFalse(PrismalGaussianBlurKernel.VERTICAL_FRAGMENT.contains("uniform float u_sigma;"));
    }

    @Test public void normalizedKernelMatchesOriginalForAllCommonSigma() {
        for (float sigma : new float[]{0.5f, 0.8f, 1f, 1.25f, 2f, 3.6f, 8f, 16f, 50f}) {
            PrismalGaussianBlurKernel kernel = new PrismalGaussianBlurKernel();
            kernel.update(sigma);
            double sum = kernel.weights[0];
            for (int i = 1; i <= 15; i++) sum += 2.0 * kernel.weights[i];
            assertEquals("sigma=" + sigma, 1.0, sum, 0.000001);
            double reconstructed = kernel.weights[0] + 2.0 * kernel.weights[15];
            for (int j = 0; j < PrismalGaussianBlurKernel.PAIR_COUNT; j++) {
                float position = kernel.verticalPairs[2*j];
                float pairWeight = kernel.verticalPairs[2*j+1];
                assertTrue(position >= 2*j+1 && position <= 2*j+2);
                assertTrue(pairWeight >= 0f);
                reconstructed += 2.0 * pairWeight;
            }
            assertEquals("paired sigma=" + sigma, 1.0, reconstructed, 0.000001);
        }
    }

    @Test public void verticalBilinearSamplingMatchesThirtyOneTapsIncludingClampedEdges() {
        Random random = new Random(130711);
        float[] pixels = new float[157];
        for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextFloat();
        PrismalGaussianBlurKernel kernel = new PrismalGaussianBlurKernel();
        for (float sigma : new float[]{0.5f, 0.7f, 1f, 1.5f, 3f, 7f, 18f, 40f}) {
            kernel.update(sigma);
            for (int center = 0; center < pixels.length; center++) {
                double original = kernel.weights[0] * pixels[center];
                for (int i = 1; i <= 15; i++) {
                    original += kernel.weights[i] *
                            (sample(pixels, center + i) + sample(pixels, center - i));
                }
                double paired = kernel.weights[0] * pixels[center];
                for (int j = 0; j < PrismalGaussianBlurKernel.PAIR_COUNT; j++) {
                    float d = kernel.verticalPairs[2*j];
                    float w = kernel.verticalPairs[2*j+1];
                    paired += w * (sample(pixels, center + d) + sample(pixels, center - d));
                }
                paired += kernel.weights[15] *
                        (sample(pixels, center + 15) + sample(pixels, center - 15));
                assertEquals("sigma=" + sigma + " pixel=" + center,
                        original, paired, 0.000002);
            }
        }
    }

    @Test public void invalidSigmasAreRejectedBeforeUniformUpload() {
        PrismalGaussianBlurKernel kernel = new PrismalGaussianBlurKernel();
        for (float invalid : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            try { kernel.update(invalid); fail("accepted invalid sigma " + invalid); }
            catch (IllegalArgumentException expected) {}
        }
    }

    private static double sample(float[] signal, float position) {
        int lo = (int)Math.floor(position);
        float alpha = (float)(position - lo);
        return at(signal, lo) * (1.0 - alpha) + at(signal, lo + 1) * alpha;
    }

    private static float at(float[] signal, int index) {
        return signal[Math.max(0, Math.min(signal.length-1, index))];
    }

    private static int occurrences(String input, String token) {
        int count = 0, index = 0;
        while ((index = input.indexOf(token, index)) >= 0) {
            count++; index += token.length();
        }
        return count;
    }
}
