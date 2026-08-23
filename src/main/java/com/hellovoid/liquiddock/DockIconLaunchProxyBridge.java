package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Cross-window visual-owner handoff for Launcher 4.50 Dock ShortcutIcon animations. */
final class DockIconLaunchProxyBridge {
    private static final WeakHashMap<View, Binding> BINDINGS = new WeakHashMap<>();

    private static final class Binding {
        final WeakReference<View> anchorRef;
        final LauncherGlassStaticNode node;
        Binding(View anchor, LauncherGlassStaticNode node) {
            anchorRef = new WeakReference<>(anchor);
            this.node = node;
        }
    }

    private DockIconLaunchProxyBridge() {}

    static void holdHidden(Object owner, View target, LiquidDockConfig.Glass glassConfig) {
        if (target == null) return;
        DockGlassItemRegistry.holdLaunchProxyHidden(target);
        Binding binding = ensureBinding(owner, target, glassConfig);
        if (binding != null && binding.node != null) binding.node.holdLaunchProxyHidden();
    }

    static void update(Object owner, View target, RectF rect, LiquidDockConfig.Glass glassConfig) {
        if (target == null || rect == null || rect.width() <= 0f || rect.height() <= 0f) return;
        DockGlassItemRegistry.updateLaunchProxyGeometry(
                target, rect.left, rect.top, rect.right, rect.bottom);
        Binding binding = ensureBinding(owner, target, glassConfig);
        if (binding != null && binding.node != null) {
            binding.node.updateLaunchProxyGeometry(rect.left, rect.top, rect.right, rect.bottom);
        }
    }

    static void end(View target) {
        if (target == null) return;
        Binding binding;
        synchronized (BINDINGS) { binding = BINDINGS.remove(target); }
        if (binding != null && binding.node != null) {
            binding.node.endLaunchProxy();
            binding.node.dispose();
        }
        DockGlassItemRegistry.endLaunchProxy(target);
    }

    static void clear() {
        ArrayList<View> targets;
        synchronized (BINDINGS) { targets = new ArrayList<>(BINDINGS.keySet()); }
        for (View target : targets) end(target);
        synchronized (BINDINGS) { BINDINGS.clear(); }
    }

    private static Binding ensureBinding(
            Object owner, View target, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || target == null || glassConfig == null
                || !glassConfig.iconStyle.enabled) return null;
        View anchor = resolveSessionAnchor(owner);
        if (anchor == null || !anchor.isAttachedToWindow()) return null;
        synchronized (BINDINGS) {
            Binding existing = BINDINGS.get(target);
            if (existing != null && existing.anchorRef.get() == anchor && existing.node != null) {
                return existing;
            }
            if (existing != null && existing.node != null) existing.node.dispose();
            LauncherGlassStaticNode node = attachLaunchProxyAnchor(
                    anchor, target, glassConfig);
            if (node == null) {
                BINDINGS.remove(target);
                return null;
            }
            Binding created = new Binding(anchor, node);
            BINDINGS.put(target, created);
            return created;
        }
    }

    private static LauncherGlassStaticNode attachLaunchProxyAnchor(
            View sessionAnchor, View proxyReference, LiquidDockConfig.Glass glassConfig) {
        try {
            LauncherGlassSession shared =
                    LauncherGlassSessionRegistry.acquire(sessionAnchor, glassConfig);
            if (shared == null) return null;
            float radius = resolveProxyReferenceRadius(proxyReference);
            Constructor<LauncherGlassStaticNode> constructor =
                    LauncherGlassStaticNode.class.getDeclaredConstructor(
                            View.class, LauncherGlassDragState.Kind.class,
                            LauncherGlassNodeKind.class, LauncherGlassSession.class,
                            float.class, LiquidDockConfig.Glass.class);
            constructor.setAccessible(true);
            // Geometry reference stays the original Dock ShortcutIcon, while the injected Session
            // belongs to the Launcher main ViewRoot. The existing proxy branch consumes vendor rects
            // before any Workspace/source visibility check, so no Dock transform leaks across windows.
            LauncherGlassStaticNode node = constructor.newInstance(
                    proxyReference, LauncherGlassDragState.Kind.ICON, LauncherGlassNodeKind.ICON,
                    shared, radius, glassConfig);
            node.holdLaunchProxyHidden();
            shared.registerStaticNode(node);
            return node;
        } catch (Throwable error) {
            MainHook.log("[DC][DockIconProxy] proxy node attach failed: " + error);
            return null;
        }
    }

    private static float resolveProxyReferenceRadius(View proxyReference) {
        if (proxyReference == null) return 0f;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(proxyReference);
        float min = icon != null && icon.width() > 0f && icon.height() > 0f
                ? Math.min(icon.width(), icon.height())
                : Math.min(Math.max(1, proxyReference.getWidth()),
                        Math.max(1, proxyReference.getHeight()));
        android.graphics.drawable.Drawable drawable = null;
        if (proxyReference instanceof android.widget.TextView) {
            android.graphics.drawable.Drawable[] drawables =
                    ((android.widget.TextView) proxyReference).getCompoundDrawables();
            if (drawables.length > 1) drawable = drawables[1];
        }
        return LauncherGlassIconShapeResolver.resolveAutoRadius(
                drawable, min, min, min * 0.22f);
    }

    private static View resolveSessionAnchor(Object owner) {
        if (owner instanceof View) return (View) owner;
        try {
            // FloatingIconLayer2 is not a View in Launcher 4.50. It stores private Launcher
            // launcher and renders its SurfaceControl against launcher.getRootView(). Workspace is
            // an attached View in that same ViewRoot, so use it only as the shared-session anchor.
            Object launcher = HookUtil.getField(owner, "launcher");
            Object workspace = HookUtil.invoke(launcher, "getWorkspace");
            return workspace instanceof View ? (View) workspace : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
