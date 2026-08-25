package com.hellovoid.liquiddock;

/** Process-wide immutable-at-launch animation timing used by injected visual owners. */
final class AnimationRuntimeState {
    private static volatile int workspaceVisibilityMs = 450;
    private static volatile int dockIconRevealMs = 450;
    private static volatile int pressInMs = 90;
    private static volatile int pressOutMs = 160;

    static void configure(LiquidDockConfig.Animation animation) {
        if (animation == null) return;
        workspaceVisibilityMs = animation.workspaceVisibilityMs;
        dockIconRevealMs = animation.dockIconRevealMs;
        pressInMs = animation.pressInMs;
        pressOutMs = animation.pressOutMs;
    }

    static int workspaceVisibilityDurationMs() { return workspaceVisibilityMs; }
    static int dockIconRevealDurationMs() { return dockIconRevealMs; }
    static int pressInDurationMs() { return pressInMs; }
    static int pressOutDurationMs() { return pressOutMs; }

    private AnimationRuntimeState() {}
}
