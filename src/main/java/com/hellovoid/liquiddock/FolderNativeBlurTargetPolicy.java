package com.hellovoid.liquiddock;

/** Single OEM BlurUtils.fastBlur target authority; no independent blur animation. */
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
            boolean nativeFolderOpening, boolean returningToFolder, float nativeFolderRatio) {
        // A zero target is an explicit native clear on Recents entry or
        // FolderCling CLOSE; the folder never overrides it.
        if (!Float.isFinite(requested) || requested <= 0f) return requested;

        // The actual native folder's configured strength is an absolute
        // target, not a ceiling. Capping at the stock 0.22 would make
        // percent settings above 22 ineffective on folder OPEN.
        if (nativeFolderOpening || returningToFolder) {
            return Math.max(0f, Math.min(1f, nativeFolderRatio));
        }
        return recentsScope ? RecentsBlurPolicy.ratioFromPercent(recentsPercent) : requested;
    }
}
