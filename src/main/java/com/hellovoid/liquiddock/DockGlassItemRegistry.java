package com.hellovoid.liquiddock;

import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak candidate registry. Domain ownership is verified again by DockGlassItemNode. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static final DockIconAnimationState ANIMATION =
            new DockIconAnimationState(AnimationRuntimeState.dockIconRevealDurationMs());
    private static long membershipRevision;
    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (!GlassRuntimeState.isIconEnabled() || view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        membershipRevision++;
    }
    static synchronized void unregister(View view) {
        if (view != null) ANIMATION.remove(view);
        if (view != null && ICONS.remove(view) != null) membershipRevision++;
    }
    static synchronized void clear() {
        ANIMATION.clear();
        if (!ICONS.isEmpty()) { ICONS.clear(); membershipRevision++; }
    }
    static synchronized void observeLaunchAnimationFrame(View view, float progress) {
        if (!GlassRuntimeState.isIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        DockAnimationTrace.animationRegistry("registry-observe", view, progress);
        if (!ANIMATION.observeProxyFrame(view, progress, SystemClock.uptimeMillis())) return;
        DockAnimationTrace.animationRegistry("registry-state-change", view, progress);
        Miuix307ZeroCopyRenderer.requestDockAnimationFrames();
    }
    static synchronized void endLaunchAnimation(View view) {
        if (!GlassRuntimeState.isIconEnabled() || view == null || !ICONS.containsKey(view)) return;
        DockAnimationTrace.animationRegistry("registry-end-pre", view, Float.NaN);
        ANIMATION.end(view, SystemClock.uptimeMillis());
        DockAnimationTrace.animationRegistry("registry-end-post", view, Float.NaN);
        if (ANIMATION.isFading(view)) {
            Miuix307ZeroCopyRenderer.requestDockAnimationFrames();
        }
    }
    static synchronized DockIconAnimationState.Sample animationSample(View view, long nowMs) {
        return ANIMATION.sample(view, nowMs);
    }
    static synchronized float animationOpacity(View view, long nowMs) {
        return ANIMATION.opacity(view, nowMs);
    }
    static synchronized boolean isFading(View view) {
        return ANIMATION.isFading(view);
    }
    static synchronized boolean hasActiveAnimation() {
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && ANIMATION.isFading(view)) return true;
        }
        return false;
    }
    static synchronized long revision() { return membershipRevision; }
    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> out = new ArrayList<>();
        if (!GlassRuntimeState.isIconEnabled() || root == null) return out;
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) out.add(view);
        }
        return out;
    }
}
