package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

public class PrismalFrameTargetTest {
    @Test public void croppedTextureKeepsLogicalViewportAndTranslatesScissor() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectTextureRegion(21, 1578, 329, 2498, 1110, 460, 390);
        assertEquals(-460, target.viewportX);
        assertEquals(-390, target.viewportY);
        assertEquals(2498, target.width);
        assertEquals(1110, target.height);
        PrismalNodeScissor.Rect rect = new PrismalNodeScissor.Rect(450, 380, 1600, 360);
        assertTrue(target.clipScissor(rect));
        assertEquals(0, rect.x);
        assertEquals(0, rect.y);
        assertEquals(1578, rect.width);
        assertEquals(329, rect.height);
    }

    @Test public void croppedTextureRejectsInvisibleNodesAndResetsForOtherTargets() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectTextureRegion(21, 100, 50, 200, 150, 50, 40);
        assertFalse(target.clipScissor(new PrismalNodeScissor.Rect(0,0,30,30)));
        target.selectTexture(17, 200, 150);
        assertEquals(0, target.viewportX);
        assertEquals(0, target.viewportY);
        PrismalNodeScissor.Rect rect = new PrismalNodeScissor.Rect(0,0,200,150);
        assertTrue(target.clipScissor(rect));
        assertEquals(200, rect.width);
        assertEquals(150, rect.height);
    }

    @Test public void unalignedCropIsRejectedBeforeReplacingTarget() {
        PrismalFrameTarget target = new PrismalFrameTarget();
        target.selectTexture(17, 200, 150);
        assertThrows(IllegalArgumentException.class,
                () -> target.selectTextureRegion(21, 100, 50, 200, 150, 51, 40));
        assertEquals(17, target.framebuffer);
        assertEquals(0, target.viewportX);
    }

    @Test public void animationDensityReducesPixelsWithoutChangingLogicalDomain() {
        assertEquals(1504, PrismalFrameTarget.scaledDimension(3008, 50));
        assertEquals(941, PrismalFrameTarget.scaledDimension(1881, 50));
        assertEquals(1881, PrismalFrameTarget.scaledDimension(1881, 100));
        assertEquals(1, PrismalFrameTarget.scaledDimension(1, 50));
    }

    @Test public void animationDensityClampsToSupportedRange() {
        assertEquals(500, PrismalFrameTarget.scaledDimension(1000, 0));
        assertEquals(1000, PrismalFrameTarget.scaledDimension(1000, 999));
        assertThrows(IllegalArgumentException.class,
                () -> PrismalFrameTarget.scaledDimension(0, 50));
    }
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
