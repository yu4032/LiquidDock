package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PassBlurRenderDomainTest {
    @Test
    public void fullScaleKeepsPhysicalAndLogicalExtentEqual() {
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(2400, 1080, 100);
        assertEquals(2400, domain.logicalWidth);
        assertEquals(1080, domain.logicalHeight);
        assertEquals(2400, domain.renderWidth);
        assertEquals(1080, domain.renderHeight);
    }

    @Test
    public void halfScaleChangesOnlyPhysicalPixelDensity() {
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(2400, 1080, 50);
        assertEquals(2400, domain.logicalWidth);
        assertEquals(1080, domain.logicalHeight);
        assertEquals(1200, domain.renderWidth);
        assertEquals(540, domain.renderHeight);
    }

    @Test
    public void normalizedAnchorIsInvariantAcrossDownsample() {
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(2400, 1080, 75);
        float logicalX = 1560f;
        float logicalY = 270f;
        float physicalX = logicalX * domain.renderWidth / domain.logicalWidth;
        float physicalY = logicalY * domain.renderHeight / domain.logicalHeight;
        assertEquals(logicalX / domain.logicalWidth,
                physicalX / domain.renderWidth, 0.000001f);
        assertEquals(logicalY / domain.logicalHeight,
                physicalY / domain.renderHeight, 0.000001f);
    }
}
