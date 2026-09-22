package com.hellovoid.liquiddock;

/** Immutable normalized values used only by the legacy Dock customization installer. */
final class DockInstallConfig {
    final boolean enabled;
    final boolean squircle;
    final int widthOffsetPx;
    final int heightOffsetPx;
    final int blurRadius;
    final int cornerOffsetPx;
    final int blurCornerOffsetPx;
    final int spacingPx;

    private DockInstallConfig(
            boolean enabled,
            boolean squircle,
            int widthOffsetPx,
            int heightOffsetPx,
            int blurRadius,
            int cornerOffsetPx,
            int blurCornerOffsetPx,
            int spacingPx) {
        this.enabled = enabled;
        this.squircle = squircle;
        this.widthOffsetPx = widthOffsetPx;
        this.heightOffsetPx = heightOffsetPx;
        this.blurRadius = blurRadius;
        this.cornerOffsetPx = cornerOffsetPx;
        this.blurCornerOffsetPx = blurCornerOffsetPx;
        this.spacingPx = spacingPx;
    }

    static DockInstallConfig from(LiquidDockConfig.Dock dock, float density) {
        return normalize(
                dock.enabled,
                dock.squircle,
                dock.widthOffset,
                dock.heightOffset,
                dock.blurRadius,
                dock.cornerOffset,
                dock.blurCornerOffset,
                dock.spacing,
                dock.dimensionsDp,
                dock.cornersDp,
                density);
    }

    static DockInstallConfig normalize(
            boolean enabled,
            boolean squircle,
            float widthOffset,
            float heightOffset,
            int blurRadius,
            float cornerOffset,
            float blurCornerOffset,
            float spacing,
            boolean dimensionsDp,
            boolean cornersDp,
            float density) {
        float dockScale = dimensionsDp ? density : 1f;
        float cornerScale = cornersDp ? density : 1f;
        return new DockInstallConfig(
                enabled,
                squircle,
                Math.round(widthOffset * dockScale),
                Math.round(heightOffset * dockScale),
                blurRadius,
                Math.round(cornerOffset * cornerScale),
                Math.round(blurCornerOffset * cornerScale),
                Math.round(spacing * dockScale));
    }
}
