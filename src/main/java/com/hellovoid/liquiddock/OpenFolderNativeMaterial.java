package com.hellovoid.liquiddock;

import java.util.IdentityHashMap;
import java.util.Iterator;

/** Draw-only ownership: never mutate native alpha, identity or animation inputs. */
final class OpenFolderNativeMaterial {
    private final OpenFolderGlassState state;
    private final IdentityHashMap<Object, Entry> materials = new IdentityHashMap<>();
    private long frame;

    OpenFolderNativeMaterial(OpenFolderGlassState state) { this.state = state; }

    boolean beginFrame(long serial) {
        if (!state.owns(serial)) return false;
        frame++;
        return true;
    }

    void track(long serial, Object material) { track(serial, material, null); }

    void track(long serial, Object material, Runnable invalidator) {
        if (material == null || !state.owns(serial)) return;
        Entry entry = materials.get(material);
        if (entry == null || entry.serial != serial) {
            if (entry != null) entry.invalidate();
            entry = new Entry(serial, invalidator);
            materials.put(material, entry);
            if (state.maySuppressNative()) entry.invalidate();
        }
        entry.frame = frame;
    }

    void endFrame(long serial) {
        if (!state.owns(serial)) return;
        Iterator<Entry> iterator = materials.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.serial != serial || entry.frame != frame) {
                iterator.remove();
                entry.invalidate();
            }
        }
    }

    void invalidateAll() { for (Entry entry : materials.values()) entry.invalidate(); }

    boolean shouldSuppress(Object material) {
        Entry entry = materials.get(material);
        return entry != null && state.owns(entry.serial) && state.maySuppressNative();
    }

    private static final class Entry {
        final long serial;
        final Runnable invalidator;
        long frame;
        Entry(long serial, Runnable invalidator) { this.serial = serial; this.invalidator = invalidator; }
        void invalidate() { if (invalidator != null) invalidator.run(); }
    }
}
