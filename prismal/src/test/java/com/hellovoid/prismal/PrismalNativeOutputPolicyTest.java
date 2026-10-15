package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

/** Typed lifecycle checks; no production-source inspection. */
public class PrismalNativeOutputPolicyTest {
    @Test public void staticWindowPathNeedsOnlyValidBackdropFramebufferSet() {
        assertTrue(PrismalNativeOutputPolicy.canReuseBackdrop(
                1504, 940, 3008, 1880,
                1504, 940, 3008, 1880,
                17, 23, 29));
        // No native output texture/FBO is required for reuse.
        assertFalse(PrismalNativeOutputPolicy.hasNativeOutput(0, 0));
    }

    @Test public void geometryResizeOrMissingBlurTargetRequiresRebuild() {
        assertFalse(PrismalNativeOutputPolicy.canReuseBackdrop(
                1504, 940, 3008, 1880,
                1504, 940, 1880, 3008,
                17, 23, 29));
        assertFalse(PrismalNativeOutputPolicy.canReuseBackdrop(
                1504, 940, 3008, 1880,
                1504, 940, 3008, 1880,
                17, 0, 29));
        assertFalse(PrismalNativeOutputPolicy.canReuseBackdrop(
                1504, 940, 3008, 1880,
                1504, 940, 3008, 1880,
                0, 23, 29));
    }

    @Test public void nativeTextureOutputMustOwnBothValidHandles() {
        assertFalse(PrismalNativeOutputPolicy.hasNativeOutput(0, 17));
        assertFalse(PrismalNativeOutputPolicy.hasNativeOutput(17, 0));
        assertTrue(PrismalNativeOutputPolicy.hasNativeOutput(17, 23));
    }
}
