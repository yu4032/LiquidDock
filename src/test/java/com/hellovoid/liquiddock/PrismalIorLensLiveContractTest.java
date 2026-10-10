package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalIorLensPolicy;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalSampling;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Protect the runtime IOR-to-edge-lens link, not only upstream Fresnel behavior. */
public class PrismalIorLensLiveContractTest {
    @Test public void lensResponsePreservesDefaultWhileRefractiveIndexControlsStrength() {
        assertEquals(0f, PrismalIorLensPolicy.relativeBend(1f), 0.00001f);
        assertEquals(1f, PrismalIorLensPolicy.relativeBend(1.55f), 0.00001f);
        assertTrue(PrismalIorLensPolicy.relativeBend(1.2f) <
                PrismalIorLensPolicy.relativeBend(1.55f));
        assertTrue(PrismalIorLensPolicy.relativeBend(2f) >
                PrismalIorLensPolicy.relativeBend(1.55f));
    }

    @Test public void runtimeRendererAndGuardApplyTheSameIorFactor() throws Exception {
        String renderer = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        String guard = Files.readString(Path.of(
                "prismal/src/main/java/com/hellovoid/prismal/PrismalSampling.java"));
        assertTrue(renderer.contains("PrismalIorLensPolicy.relativeBend(p.ior)"));
        assertTrue(guard.contains("PrismalIorLensPolicy.relativeBend(p.ior)"));

        PrismalParams.Builder low = PrismalParams.builder();
        low.ior = 1.0f;
        low.heightTransitionWidthPx = 32f;
        low.lensRefractionScale = 2f;
        PrismalParams.Builder high = PrismalParams.builder();
        high.ior = 2.0f;
        high.heightTransitionWidthPx = 32f;
        high.lensRefractionScale = 2f;
        assertTrue(PrismalSampling.requiredGuardPx(high.build(), 360f, 180f, true)
                > PrismalSampling.requiredGuardPx(low.build(), 360f, 180f, true));
    }
}
