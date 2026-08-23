package com.hellovoid.liquiddock;

import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak ShortcutIcon registry; Dock compositor only rescans candidates when this registry changes. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static long revision;

    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        revision++;
    }

    static synchronized long revision() {
        return revision;
    }

    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> result = new ArrayList<>();
        if (root == null) return result;
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) {
                result.add(view);
            }
        }
        return result;
    }
}
