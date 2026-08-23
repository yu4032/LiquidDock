package com.hellovoid.liquiddock;

import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak ShortcutIcon registry so Dock snapshots never recursively scan the View tree per frame. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();

    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (view != null) ICONS.put(view, Boolean.TRUE);
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
