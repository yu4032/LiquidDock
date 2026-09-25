package com.hellovoid.liquiddock;

/** Pure render decision for Dock icon-scene changes that do not alter the backdrop mapping. */
final class DockGlassSceneRenderPolicy {
    private DockGlassSceneRenderPolicy() {}

    static boolean shouldRenderSceneOnlyChange(
            boolean dockSceneChanged, boolean hasFreshProducerFrame) {
        return dockSceneChanged && hasFreshProducerFrame;
    }
}
