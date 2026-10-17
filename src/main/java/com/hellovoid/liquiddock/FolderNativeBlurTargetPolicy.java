package com.hellovoid.liquiddock;

/** Single OEM BlurUtils.fastBlur target authority; no independent blur animation. */
final class FolderNativeBlurTargetPolicy {
    private FolderNativeBlurTargetPolicy() {}

    /** Launcher 4.50 HomeState's full-strength target is emitted before
     * onRecentViewHide. Distinguish it from explicitly scoped Recents blur
     * and from ordinary HOME without a still-open native folder.
     */
    static boolean isCoveredRecentsHomeTarget(
            boolean recentsCovered, boolean expandedFolderOpen,
            boolean recentsTargetScope, float requested) {
        return recentsCovered && expandedFolderOpen && !recentsTargetScope
                && Float.isFinite(requested) && requested >= .999f;
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
