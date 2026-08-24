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
        final DockIconFrozenGlassLayer layer;
        LauncherGlassStaticNode fallbackNode;

        Binding(View anchor, DockIconFrozenGlassLayer layer, LauncherGlassStaticNode fallbackNode) {
            anchorRef = new WeakReference<>(anchor);
            this.layer = layer;
            this.fallbackNode = fallbackNode;
        }
    }

    private DockIconLaunchProxyBridge() {}

    static void holdHidden(Object owner, View target, LiquidDockConfig.Glass glassConfig) {
        if (target == null) return;
        // Capture the final static Dock glass before marking the Dock item as proxy-owned. Once the
        // registry yields, DockGlassItemNode.capture() intentionally returns null.
        Binding binding = ensureBinding(owner, target, glassConfig);
        DockGlassItemRegistry.holdLaunchProxyHidden(target);
        if (binding == null) return;
        if (binding.layer != null && !binding.layer.isFailed()) {
            binding.layer.holdHidden();
            return;
        }
        LauncherGlassStaticNode fallback = ensureFallback(binding, target, glassConfig);
        if (fallback != null) fallback.holdLaunchProxyHidden();
    }

    static void update(
            Object owner,
            View target,
            RectF rect,
            Object vendorTransaction,
            LiquidDockConfig.Glass glassConfig) {
        if (target == null || rect == null || rect.width() <= 0f || rect.height() <= 0f) return;
        Binding binding = ensureBinding(owner, target, glassConfig);
        DockGlassItemRegistry.holdLaunchProxyHidden(target);
        if (binding == null) return;
        if (binding.layer != null && !binding.layer.isFailed()) {
            binding.layer.update(owner, rect, vendorTransaction);
            return;
        }
        LauncherGlassStaticNode fallback = ensureFallback(binding, target, glassConfig);
        if (fallback != null) {
            fallback.updateLaunchProxyGeometry(rect.left, rect.top, rect.right, rect.bottom);
        }
    }

    static void end(View target) {
        if (target == null) return;
        Binding binding;
        synchronized (BINDINGS) { binding = BINDINGS.remove(target); }
        if (binding != null) {
            if (binding.layer != null) binding.layer.release();
            LauncherGlassStaticNode fallback = binding.fallbackNode;
            if (fallback != null) {
                fallback.endLaunchProxy();
                fallback.dispose();
            }
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
            if (existing != null && existing.anchorRef.get() == anchor) return existing;
            if (existing != null) releaseBinding(existing);

            DockIconFrozenGlassLayer frozen =
                    DockIconFrozenGlassLayer.tryCreate(owner, anchor, target);
            LauncherGlassStaticNode fallback = frozen == null
                    ? attachLaunchProxyAnchor(anchor, target, glassConfig)
                    : null;
            if (frozen == null && fallback == null) {
                BINDINGS.remove(target);
                return null;
            }
            Binding created = new Binding(anchor, frozen, fallback);
            BINDINGS.put(target, created);
            return created;
        }
    }

    private static LauncherGlassStaticNode ensureFallback(
            Binding binding, View target, LiquidDockConfig.Glass glassConfig) {
        if (binding == null) return null;
        LauncherGlassStaticNode current = binding.fallbackNode;
        if (current != null) return current;
        View anchor = binding.anchorRef.get();
        if (anchor == null) return null;
        LauncherGlassStaticNode created = attachLaunchProxyAnchor(anchor, target, glassConfig);
        binding.fallbackNode = created;
        return created;
    }

    private static void releaseBinding(Binding binding) {
        if (binding == null) return;
        if (binding.layer != null) binding.layer.release();
        if (binding.fallbackNode != null) {
            binding.fallbackNode.endLaunchProxy();
            binding.fallbackNode.dispose();
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
            LauncherGlassStaticNode node = constructor.newInstance(
                    proxyReference, LauncherGlassDragState.Kind.ICON, LauncherGlassNodeKind.ICON,
                    shared, radius, glassConfig);
            node.holdLaunchProxyHidden();
            shared.registerStaticNode(node);
            return node;
        } catch (Throwable error) {
            MainHook.log("[DC][DockIconProxy] fallback node attach failed: " + error);
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
            Object launcher = HookUtil.getField(owner, "launcher");
            Object workspace = HookUtil.invoke(launcher, "getWorkspace");
            return workspace instanceof View ? (View) workspace : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
