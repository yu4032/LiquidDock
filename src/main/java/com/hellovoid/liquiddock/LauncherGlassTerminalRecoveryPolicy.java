package com.hellovoid.liquiddock;

/** Pure ownership/generation gate for Launcher Workspace terminal producer recovery. */
final class LauncherGlassTerminalRecoveryPolicy {
    private LauncherGlassTerminalRecoveryPolicy() {}

    static boolean shouldSelfRecover(
            long failureGeneration, long sceneGeneration, boolean externallyOwned) {
        if (externallyOwned) return false;
        return failureGeneration < 0L || failureGeneration == sceneGeneration;
    }
}
