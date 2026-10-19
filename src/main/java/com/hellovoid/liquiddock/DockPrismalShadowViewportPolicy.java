package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalEdgeDecoration;

/**
 * Size the Dock's ONLY TextureView output to include Prismal's exterior cast
 * shadow. Sampling insets alone cannot make outside pixels visible: the final
 * GL composite and Android TextureView must also contain those pixels.
 *
 * Native Dock geometry is not inset, scaled or recentered.
 */
final class DockPrismalShadowViewportPolicy {
    private DockPrismalShadowViewportPolicy() {}

    static int marginPx(PrismalEdgeDecoration style, float density, int nativeShortPx) {
        if (style == null || !style.hasShadow() || nativeShortPx <= 0) return 0;
        float shadow = style.extraGuardPx(density, nativeShortPx);
        return Math.max(0, Math.min(96, (int) Math.ceil(shadow + 2f)));
    }

    /** Where the true native Dock body lives inside an expanded output. */
    static float normalizedBodyX(int insetLeft, int localDockLeft, int sampleWidth) {
        return (insetLeft + localDockLeft) / (float) Math.max(1, sampleWidth);
    }

    static float normalizedBodyBottom(
            int insetBottom, int outputHeight, int localDockTop,
            int dockHeight, int sampleHeight) {
        return (insetBottom + outputHeight - localDockTop - dockHeight)
                / (float) Math.max(1, sampleHeight);
    }

    /** Full output crop, not the smaller native Dock crop. */
    static float normalizedOutputOrigin(int sampleInset, int sampleExtent) {
        return sampleInset / (float) Math.max(1, sampleExtent);
    }

    static float normalizedOutputExtent(int outputExtent, int sampleExtent) {
        return outputExtent / (float) Math.max(1, sampleExtent);
    }
}
