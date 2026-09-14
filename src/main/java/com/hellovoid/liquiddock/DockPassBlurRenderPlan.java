package com.hellovoid.liquiddock;

/** Dock backdrop physical pixel plan; glass and output remain in logical sample coordinates. */
final class DockPassBlurRenderPlan {
    final int logicalWidth;
    final int logicalHeight;
    final int physicalWidth;
    final int physicalHeight;
    final int outputWidth;
    final int outputHeight;

    private DockPassBlurRenderPlan(
            int logicalWidth,
            int logicalHeight,
            int physicalWidth,
            int physicalHeight) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.physicalWidth = physicalWidth;
        this.physicalHeight = physicalHeight;
        outputWidth = logicalWidth;
        outputHeight = logicalHeight;
    }

    static DockPassBlurRenderPlan resolve(
            int logicalWidth,
            int logicalHeight,
            int requestedScalePercent) {
        PassBlurRenderDomain domain = PassBlurRenderDomain.resolve(
                logicalWidth, logicalHeight, requestedScalePercent);
        return new DockPassBlurRenderPlan(
                domain.logicalWidth,
                domain.logicalHeight,
                domain.renderWidth,
                domain.renderHeight);
    }
}
