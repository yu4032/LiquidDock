package com.hellovoid.liquiddock;

import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak candidate registry. Domain ownership is verified again by DockGlassItemNode. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static final DockIconAnimationState ANIMATION = new DockIconAnimationState();
    private static long membershipRevision;
    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        view.invalidate();
        membershipRevision++;
    }
    static synchronized void unregister(View view) {
        if (view != null) ANIMATION.remove(view);
        if (view != null && ICONS.remove(view) != null) { membershipRevision++; view.invalidate(); }
    }
    static synchronized boolean contains(View view) { return view != null && ICONS.containsKey(view); }
    static synchronized void clear() {
        ANIMATION.clear();
        if (!ICONS.isEmpty()) { ICONS.clear(); membershipRevision++; }
    }
    static synchronized void beginLaunchAnimation(View view) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.onNativeHidden(view)) {
            Miuix307ZeroCopyRenderer.requestDockProgressRefresh();
        }
    }

    static synchronized void cancelHomeReturn(View view) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.cancelHomeReturn(view)) {
            Miuix307ZeroCopyRenderer.requestDockProgressRefresh();
        }
    }

    static synchronized void observeHomeReturnProgress(View view, float progress) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.observeHomeReturnProgress(view, progress)) {
            Miuix307ZeroCopyRenderer.requestDockProgressRefresh();
        }
    }
    static synchronized void holdProxyHidden(View view) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.holdProxyHidden(view)) {
            Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
        }
    }
    static synchronized void updateProxyGeometry(View view, float left, float top,
            float right, float bottom) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.updateProxyGeometry(view, new float[]{left, top, right, bottom})) {
            Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
        }
    }
    static synchronized void endProxyGeometry(View view) {
        if (view == null) return;
        if (ANIMATION.endProxyGeometry(view)) {
            Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
        }
    }
    static synchronized void endLaunchAnimation(View view) {
        if (!GlassRuntimeState.isAnyIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        if (ANIMATION.end(view)) {
            Miuix307ZeroCopyRenderer.requestDockProgressRefresh();
        }
    }
    static synchronized boolean isProxyActive(View view) {
        return ANIMATION.isProxyActive(view);
    }

    static synchronized DockIconAnimationState.Sample animationSample(View view) {
        return ANIMATION.sample(view);
    }
    static synchronized long revision() { return membershipRevision; }
    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> out = new ArrayList<>();
        if (!GlassRuntimeState.isAnyIconEnabled() || root == null) return out;
        for (View view : ICONS.keySet()) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) out.add(view);
        }
        return out;
    }
}
