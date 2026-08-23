package com.hellovoid.liquiddock;

/** Immutable UI-thread Dock geometry consumed by the Dock GL thread. */
final class DockGlassSceneSnapshot {
    static final DockGlassSceneSnapshot EMPTY =
            new DockGlassSceneSnapshot(new LauncherGlassGeometry.Snapshot[0]);
    final LauncherGlassGeometry.Snapshot[] items;
    DockGlassSceneSnapshot(LauncherGlassGeometry.Snapshot[] items) {
        this.items = items != null ? items.clone() : new LauncherGlassGeometry.Snapshot[0];
    }
    int size() { return items.length; }
}
