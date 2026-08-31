package com.hellovoid.liquiddock;

/** Coalesces Dock glass work so scene-only animation frames can reuse the prepared backdrop. */
final class DockGlassFramePolicy {
    private boolean sourceDirty;
    private boolean mappingDirty;
    private boolean sceneDirty;

    synchronized void requestSource() {
        sourceDirty = true;
        sceneDirty = true;
    }

    synchronized void requestMapping() {
        mappingDirty = true;
        sceneDirty = true;
    }

    synchronized void requestScene() {
        sceneDirty = true;
    }

    synchronized Work consume() {
        boolean prepareBackdrop = sourceDirty || mappingDirty;
        boolean renderScene = prepareBackdrop || sceneDirty;
        sourceDirty = false;
        mappingDirty = false;
        sceneDirty = false;
        return new Work(prepareBackdrop, renderScene);
    }

    static final class Work {
        final boolean prepareBackdrop;
        final boolean renderScene;

        Work(boolean prepareBackdrop, boolean renderScene) {
            this.prepareBackdrop = prepareBackdrop;
            this.renderScene = renderScene;
        }
    }
}
