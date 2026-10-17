package com.hellovoid.liquiddock;

/** Native BlurUtils.fastBlur target arbitration; no independent blur animator. */
final class FolderNativeBlurTargetPolicy {
    private FolderNativeBlurTargetPolicy() {}

    static boolean shouldArmBeforeOverviewExit(boolean recentsCovered, boolean expandedFolderOpen) {
        return recentsCovered && expandedFolderOpen;
    }

    static float capWhenFolderOwns(
            float requested, boolean nativeOpening, boolean recentsReturningToFolder, float cap) {
        if (!nativeOpening && !recentsReturningToFolder) return requested;
        return Math.max(0f, Math.min(requested, cap));
    }
}
