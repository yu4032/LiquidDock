package com.hellovoid.liquiddock;

/**
 * A static EGL TextureView output needs one explicit transparent swap per new
 * surface/resize or after content disappears. Further empty frames can reuse
 * that *presented* transparent buffer without touching BufferQueue.
 *
 * A content-bearing scene is never skipped: new wallpaper frames, alpha fades,
 * spring transforms and widget geometry all keep their original live updates.
 */
final class WorkspaceStaticOutputPolicy {
    private WorkspaceStaticOutputPolicy() {}

    static boolean shouldRender(boolean blankPresented, boolean hasDrawableNode) {
        return hasDrawableNode || !blankPresented;
    }
}
