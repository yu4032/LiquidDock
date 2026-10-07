package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

public class PrismalFrameTargetTest {
    @Test public void directFrameUsesWindowDimensionsAndDefaultFramebuffer() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectTexture(17, 1880, 3008);
        target.selectSurface(3008, 1880);
        assertEquals(0, target.framebuffer);
        assertEquals(3008, target.width);
        assertEquals(1880, target.height);
    }

    @Test public void textureFrameRestoresItsOwnTargetAfterSurfaceResize() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectSurface(3008, 1880);
        target.selectTexture(23, 1880, 3008);
        assertEquals(23, target.framebuffer);
        assertEquals(1880, target.width);
        assertEquals(3008, target.height);
    }

    @Test public void rejectedSurfaceDimensionsDoNotOverwritePreviousTarget() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectTexture(17, 1880, 3008);
        assertThrows(IllegalArgumentException.class, () -> target.selectSurface(0, 1880));
        assertEquals(17, target.framebuffer);
        assertEquals(1880, target.width);
        assertEquals(3008, target.height);
    }
}
