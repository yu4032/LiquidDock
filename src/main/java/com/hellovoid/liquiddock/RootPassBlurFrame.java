package com.hellovoid.liquiddock;

/**
 * One normalized root PassBlur frame. The texture remains backend-owned; consumers may retain this
 * metadata only while rendering through the same backend GL context and must reject it after source
 * rebind/geometry invalidation or texture destruction.
 */
final class RootPassBlurFrame {
    final long generation;
    final int normalizedTextureId;
    final int logicalWidth;
    final int logicalHeight;
    final int physicalWidth;
    final int physicalHeight;
    final int rotation;
    final RootPassBlurContentRect contentRect;

    RootPassBlurFrame(
            long generation,
            int normalizedTextureId,
            int logicalWidth,
            int logicalHeight,
            int physicalWidth,
            int physicalHeight,
            int rotation,
            RootPassBlurContentRect contentRect) {
        this.generation = generation;
        this.normalizedTextureId = normalizedTextureId;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.physicalWidth = physicalWidth;
        this.physicalHeight = physicalHeight;
        this.rotation = rotation;
        this.contentRect = contentRect != null ? contentRect : RootPassBlurContentRect.full();
    }
}
