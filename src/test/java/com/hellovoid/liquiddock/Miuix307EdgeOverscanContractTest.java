package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalSampling;
import java.lang.reflect.Method;
import org.junit.Test;

public class Miuix307EdgeOverscanContractTest {
    @Test
    public void opticalSamplingGuardCoversOfficialPrismalDisplacementBudget() {
        PrismalParams defaults = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.defaults(1f));
        int horizontal = PrismalSampling.requiredGuardPx(defaults, 2302, 233, true);
        int vertical = PrismalSampling.requiredGuardPx(defaults, 2302, 233, false);

        assertTrue("default horizontal optics need substantially more than the old fixed 32dp ring",
                horizontal >= 256);
        assertTrue("default vertical optics must exceed the historical 16px bottom guard",
                vertical >= 128);
        assertTrue("wide Dock chromatic/bulge reach should require more horizontal guard",
                horizontal > vertical);
    }

    @Test
    public void customOs4ReflectionOffsetExpandsTheLivePortableSamplingGuard() {
        PrismalParams.Builder defaultsBuilder = PrismalParams.builder();
        defaultsBuilder.os4ReflectOffsetPx = 0f;
        PrismalParams.Builder customBuilder = PrismalParams.builder();
        customBuilder.os4ReflectOffsetPx = 40f;

        int defaults = PrismalSampling.requiredGuardPx(
                defaultsBuilder.build(), 96f, 96f, true);
        int custom = PrismalSampling.requiredGuardPx(
                customBuilder.build(), 96f, 96f, true);

        assertTrue("the live capture guard must cover a user-selected OS4 reflection offset",
                custom > defaults);
    }

    @Test
    public void oversizedSamplingInsetsFitInsideTheGpuTextureLimitWithoutLosingAsymmetry() throws Exception {
        Method method;
        try {
            method = Miuix307PassBlurTextureView.class.getDeclaredMethod(
                    "fitInsetPairToTextureLimit",
                    int.class, int.class, int.class, int.class, int[].class);
        } catch (NoSuchMethodException missing) {
            fail("fitInsetPairToTextureLimit must constrain overscan before FBO allocation");
            return;
        }
        method.setAccessible(true);

        int[] symmetric = new int[2];
        method.invoke(null, 2302, 3500, 3500, 8192, symmetric);
        assertEquals(5890, symmetric[0] + symmetric[1]);
        assertTrue(Math.abs(symmetric[0] - symmetric[1]) <= 1);

        int[] asymmetric = new int[2];
        method.invoke(null, 2302, 4000, 2000, 8192, asymmetric);
        assertEquals(5890, asymmetric[0] + asymmetric[1]);
        assertTrue("hardware limiting should preserve the requested left/right proportion",
                Math.abs(asymmetric[0] - asymmetric[1] * 2) <= 2);

        int[] alreadyFits = new int[2];
        method.invoke(null, 2302, 300, 150, 8192, alreadyFits);
        assertEquals(300, alreadyFits[0]);
        assertEquals(150, alreadyFits[1]);
    }
}
