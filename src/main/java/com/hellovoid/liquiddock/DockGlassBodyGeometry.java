package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import com.hellovoid.prismal.PrismalGeometry;

/**
 * Fits the optional Dock exterior hairline INSIDE the existing native
 * TextureView output. The GL source FBO already has sampling overscan, but
 * final compositing crops to the exact native Dock viewport. Insetting only
 * its visible body geometry avoids an extra Surface, parent layout changes,
 * producer recapture or expanding the native MiShadow owner.
 */
final class DockGlassBodyGeometry {
    private DockGlassBodyGeometry() {}

    static PrismalGeometry fitOuterHairline(PrismalGeometry body,
                                             PrismalEdgeDecoration optical) {
        if (optical == null || !optical.hasBacklit()) return body;
        float minSize = Math.min(body.glassWidth, body.glassHeight);
        // The GLSL receives edgeDensity = 1 in the Dock compositor.
        float needed = optical.extraGuardPx(1f, minSize);
        float inset = Math.min(needed, Math.max(0f, (minSize - 2f) * 0.2f));
        if (inset < 0.01f) return body;
        float width = body.glassWidth - inset * 2f;
        float height = body.glassHeight - inset * 2f;
        if (width < 1f || height < 1f) return body;
        return new PrismalGeometry(body.framebufferWidth, body.framebufferHeight,
                body.centerX, body.centerY, width, height,
                Math.max(0f, body.topLeftRadius - inset),
                Math.max(0f, body.topRightRadius - inset),
                Math.max(0f, body.bottomRightRadius - inset),
                Math.max(0f, body.bottomLeftRadius - inset));
    }
}
