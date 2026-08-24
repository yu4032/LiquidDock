package com.hellovoid.liquiddock;

/** Resolves Dock icon glass without changing the Dock body or Workspace geometry. */
final class WorkstationDockIconRadiusPolicy {
    private WorkstationDockIconRadiusPolicy() {}

    static float resolve(float baseRadiusDp, float absoluteRadiusDp,
                         float ignoredDensity, boolean workstationMode) {
        float base = Math.max(0f, baseRadiusDp);
        return workstationMode && absoluteRadiusDp > 0f
                ? Math.max(0f, absoluteRadiusDp) : base;
    }
}
