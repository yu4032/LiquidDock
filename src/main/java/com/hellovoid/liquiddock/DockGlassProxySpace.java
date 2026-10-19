package com.hellovoid.liquiddock;

/** Android view-global matrices omit the containing window's screen offset. */
final class DockGlassProxySpace {
    private DockGlassProxySpace() {}

    static float windowCoordinate(float screenCoordinate, int rootScreen, float rootGlobal) {
        return screenCoordinate - rootScreen + rootGlobal;
    }
}
