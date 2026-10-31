package com.hellovoid.liquiddock;

/** Process-wide immutable-at-launch animation timing used by injected visual owners. */
final class AnimationRuntimeState {
    private static volatile int workspaceVisibilityMs = 450;
    private static volatile int appReturnGlassFadeStartBeforeEndMs = 600;
    private static volatile int appReturnGlassFadeDurationMs = 450;
    private static volatile int unlockGlassFadeMs = 450;
    private static volatile int pressInMs = 90;
    private static volatile int pressOutMs = 160;
    private static volatile int shortcutPopupDismissFadeMs = 90;
    private static volatile int securityCenterExitFadeMs = 80;

    static void configure(LiquidDockConfig.Animation animation) {
        if (animation == null) return;
        workspaceVisibilityMs = animation.workspaceVisibilityMs;
        appReturnGlassFadeStartBeforeEndMs = animation.appReturnGlassFadeStartBeforeEndMs;
        appReturnGlassFadeDurationMs = animation.appReturnGlassFadeDurationMs;
        unlockGlassFadeMs = animation.unlockGlassFadeMs;
        pressInMs = animation.pressInMs;
        pressOutMs = animation.pressOutMs;
        shortcutPopupDismissFadeMs = animation.shortcutPopupDismissFadeMs;
        securityCenterExitFadeMs = animation.securityCenterExitFadeMs;
    }

    static int workspaceVisibilityDurationMs() { return workspaceVisibilityMs; }
    static int appReturnGlassFadeStartBeforeEndMs() { return appReturnGlassFadeStartBeforeEndMs; }
    static int appReturnGlassFadeDurationMs() { return appReturnGlassFadeDurationMs; }
    static int unlockGlassFadeDurationMs() { return unlockGlassFadeMs; }
    static int pressInDurationMs() { return pressInMs; }
    static int pressOutDurationMs() { return pressOutMs; }
    static int shortcutPopupDismissFadeDurationMs() { return shortcutPopupDismissFadeMs; }
    static int securityCenterExitFadeDurationMs() { return securityCenterExitFadeMs; }

    private AnimationRuntimeState() {}
}
