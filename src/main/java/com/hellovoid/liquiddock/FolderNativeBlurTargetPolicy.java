package com.hellovoid.liquiddock;

/** Native BlurUtils.fastBlur target arbitration; no independent blur animator. */
final class FolderNativeBlurTargetPolicy {
    private FolderNativeBlurTargetPolicy() {}

    static boolean shouldArmBeforeOverviewExit(boolean recentsCovered, boolean expandedFolderOpen) {
        return recentsCovered && expandedFolderOpen;
    }

    static float ratioFromPercent(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    static float arbitrate(
            float requested, boolean recentsScope, int recentsPercent,
            boolean opening, boolean returning, float folderCap) {
        float afterRecents = recentsScope && requested > 0f
                ? RecentsBlurPolicy.ratioFromPercent(recentsPercent) : requested;
        return capWhenFolderOwns(afterRecents, opening, returning, folderCap);
    }

    static float capWhenFolderOwns(
            float requested, boolean nativeOpening, boolean recentsReturningToFolder, float cap) {
        if (!nativeOpening && !recentsReturningToFolder) return requested;
        return Math.max(0f, Math.min(requested, cap));
    }
}
