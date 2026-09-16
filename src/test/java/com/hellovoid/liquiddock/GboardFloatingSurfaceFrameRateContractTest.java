package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for keeping Gboard glass presentation on the display refresh cadence. */
public class GboardFloatingSurfaceFrameRateContractTest {
    @Test public void outputSurfaceRequestsDisplayRefreshRate() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/GboardFloatingGlassView.java"));
        assertTrue(source.contains("getDisplay().getRefreshRate()"));
        assertTrue(source.contains("setFrameRate("));
        assertTrue(source.contains("Surface.FRAME_RATE_COMPATIBILITY_DEFAULT"));
    }
}
