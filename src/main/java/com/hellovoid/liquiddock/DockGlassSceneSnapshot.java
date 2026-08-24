package com.hellovoid.liquiddock;

/** Immutable UI-thread Dock geometry consumed by the Dock GL thread. */
final class DockGlassSceneSnapshot {
    static final DockGlassSceneSnapshot EMPTY =
            new DockGlassSceneSnapshot(new Item[0]);
    static final class Item {
        final LauncherGlassGeometry.Snapshot geometry;
        final float opacity;
        Item(LauncherGlassGeometry.Snapshot geometry, float opacity) {
            this.geometry = geometry;
            this.opacity = Math.max(0f, Math.min(1f, opacity));
        }
    }
    final Item[] items;
    DockGlassSceneSnapshot(Item[] items) {
        this.items = items != null ? items.clone() : new Item[0];
    }
    int size() { return items.length; }
}
