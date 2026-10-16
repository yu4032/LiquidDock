package com.hellovoid.liquiddock;

/** Preview alpha belongs to the source icon only after native close completion.
 * Workspace/ancestor opacity remains OEM-owned.
 */
final class FolderPreviewClosePolicy {
    private FolderPreviewClosePolicy() {}

    static boolean shouldRestore(boolean nativeWorkspaceVisible,
                                 boolean previewViewVisible, float previewAlpha) {
        return nativeWorkspaceVisible && previewViewVisible
                && Float.isFinite(previewAlpha) && previewAlpha < 0.999f;
    }
}
