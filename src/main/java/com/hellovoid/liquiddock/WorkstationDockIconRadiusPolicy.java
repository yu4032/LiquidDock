package com.hellovoid.liquiddock;

/** Resolves Dock icon glass without changing the Dock body or Workspace geometry. */
final class WorkstationDockIconRadiusPolicy {
    private WorkstationDockIconRadiusPolicy() {}

    static float renderedRadius(float radiusDp, float density, float localWidth,
            float localHeight, float renderedWidth, float renderedHeight) {
        float scale = Math.min(renderedWidth / Math.max(1f, localWidth),
                renderedHeight / Math.max(1f, localHeight));
        return Math.max(0f, radiusDp * density * scale);
    }

    static float resolve(float baseRadiusDp, float absoluteRadiusDp,
                         float ignoredDensity, boolean workstationMode) {
        float base = Math.max(0f, baseRadiusDp);
        return workstationMode && absoluteRadiusDp > 0f
                ? Math.max(0f, absoluteRadiusDp) : base;
    }
}
