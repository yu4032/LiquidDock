package com.hellovoid.liquiddock;

import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak candidate registry. Domain ownership is verified again by DockGlassItemNode. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static final WeakHashMap<View, LauncherGlassVisualOwnerState> VISUAL_OWNERS =
            new WeakHashMap<>();
    private static long revision;
    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        revision++;
    }
    static synchronized void unregister(View view) {
        if (view == null) return;
        DockIconLaunchProxyBridge.end(view);
        boolean changed = ICONS.remove(view) != null;
        changed |= VISUAL_OWNERS.remove(view) != null;
        if (changed) revision++;
    }
    static synchronized void clear() {
        if (!ICONS.isEmpty() || !VISUAL_OWNERS.isEmpty()) {
            ICONS.clear();
            VISUAL_OWNERS.clear();
            revision++;
        }
    }
    static synchronized boolean holdLaunchProxyHidden(View view) {
        if (view == null) return false;
        LauncherGlassVisualOwnerState state = VISUAL_OWNERS.computeIfAbsent(
                view, ignored -> new LauncherGlassVisualOwnerState());
        boolean wasActive = state.isLaunchProxyActive();
        boolean changed = state.holdLaunchProxyHidden();
        if (!wasActive && state.isLaunchProxyActive()) {
            revision++;
            invalidate(view);
        }
        return changed;
    }
    static synchronized boolean updateLaunchProxyGeometry(
            View view, float left, float top, float right, float bottom) {
        if (view == null) return false;
        LauncherGlassVisualOwnerState state = VISUAL_OWNERS.computeIfAbsent(
                view, ignored -> new LauncherGlassVisualOwnerState());
        boolean wasActive = state.isLaunchProxyActive();
        boolean changed = state.updateLaunchProxyRect(new float[]{left, top, right, bottom});
        if (!wasActive && state.isLaunchProxyActive()) {
            revision++;
            invalidate(view);
        }
        return changed;
    }
    static synchronized void endLaunchProxy(View view) {
        if (view == null) return;
        LauncherGlassVisualOwnerState state = VISUAL_OWNERS.get(view);
        if (state == null || !state.endLaunchProxy()) return;
        VISUAL_OWNERS.remove(view);
        revision++;
        invalidate(view);
    }
    static synchronized boolean isLaunchProxyActive(View view) {
        LauncherGlassVisualOwnerState state = view != null ? VISUAL_OWNERS.get(view) : null;
        return state != null && state.isLaunchProxyActive();
    }
    static synchronized long revision() { return revision; }
    private static void invalidate(View view) {
        View root = view != null ? view.getRootView() : null;
        if (root != null && root.isAttachedToWindow()) root.postInvalidateOnAnimation();
    }
    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> out = new ArrayList<>();
        if (root == null) return out;
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) out.add(view);
        }
        return out;
    }
}
