package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalGeometry;

/** Immutable UI-thread Dock geometry consumed by the Dock GL thread. */
final class DockGlassSceneSnapshot {
    static final DockGlassSceneSnapshot EMPTY =
            new DockGlassSceneSnapshot(new Item[0], true);
    static final class Item {
        final LauncherGlassGeometry.Snapshot geometry;
        final PrismalGeometry prismalGeometry;
        final float opacity;

        Item(LauncherGlassGeometry.Snapshot geometry, float opacity) {
            this(geometry, null, opacity);
        }

        Item(
                LauncherGlassGeometry.Snapshot geometry,
                PrismalGeometry prismalGeometry,
                float opacity) {
            this.geometry = geometry;
            this.prismalGeometry = prismalGeometry;
            this.opacity = Math.max(0f, Math.min(1f, opacity));
        }
    }

    final Item[] items;

    DockGlassSceneSnapshot(Item[] items) {
        this(items, false);
    }

    static DockGlassSceneSnapshot takeOwnership(Item[] items) {
        return new DockGlassSceneSnapshot(items, true);
    }

    private DockGlassSceneSnapshot(Item[] items, boolean takeOwnership) {
        if (items == null || items.length == 0) {
            this.items = new Item[0];
        } else {
            this.items = takeOwnership ? items : items.clone();
        }
    }
    int size() { return items.length; }
}
