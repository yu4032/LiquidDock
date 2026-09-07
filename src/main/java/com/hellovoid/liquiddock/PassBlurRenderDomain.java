package com.hellovoid.liquiddock;

/** Separates authoritative logical root coordinates from local physical render pixels. */
final class PassBlurRenderDomain {
    final int logicalWidth;
    final int logicalHeight;
    final int renderWidth;
    final int renderHeight;

    private PassBlurRenderDomain(
            int logicalWidth, int logicalHeight, int renderWidth, int renderHeight) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
    }

    static PassBlurRenderDomain resolve(
            int logicalWidth, int logicalHeight, int requestedScalePercent) {
        int safeLogicalWidth = Math.max(1, logicalWidth);
        int safeLogicalHeight = Math.max(1, logicalHeight);
        float scale = PassBlurQualityPolicy.captureScale(requestedScalePercent);
        int renderWidth = Math.max(1, Math.round(safeLogicalWidth * scale));
        int renderHeight = Math.max(1, Math.round(safeLogicalHeight * scale));
        return new PassBlurRenderDomain(
                safeLogicalWidth, safeLogicalHeight, renderWidth, renderHeight);
    }
}
