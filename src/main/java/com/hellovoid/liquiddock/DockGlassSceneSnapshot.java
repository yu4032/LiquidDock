package com.hellovoid.liquiddock;

/** Immutable UI-thread Dock geometry generation consumed by the GL thread. */
final class DockGlassSceneSnapshot {
    static final DockGlassSceneSnapshot EMPTY =
            new DockGlassSceneSnapshot(new LauncherGlassGeometry.Snapshot[0]);

    final LauncherGlassGeometry.Snapshot[] items;

    DockGlassSceneSnapshot(LauncherGlassGeometry.Snapshot[] items) {
        this.items = items != null ? items.clone() : new LauncherGlassGeometry.Snapshot[0];
    }

    int size() { return items.length; }

    boolean sameAs(DockGlassSceneSnapshot other) {
        if (other == this) return true;
        if (other == null || items.length != other.items.length) return false;
        for (int i = 0; i < items.length; i++) {
            LauncherGlassGeometry.Snapshot left = items[i];
            LauncherGlassGeometry.Snapshot right = other.items[i];
            if ((left == null) != (right == null)) return false;
            if (left != null && !left.sameAs(right)) return false;
        }
        return true;
    }
}
