package com.hellovoid.liquiddock;

import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak candidate registry. Domain ownership is verified again by DockGlassItemNode. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static long revision;
    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        revision++;
    }
    static synchronized void unregister(View view) {
        if (view != null && ICONS.remove(view) != null) revision++;
    }
    static synchronized void clear() {
        if (!ICONS.isEmpty()) { ICONS.clear(); revision++; }
    }
    static synchronized long revision() { return revision; }
    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> out = new ArrayList<>();
        if (root == null) return out;
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) out.add(view);
        }
        return out;
    }
}
