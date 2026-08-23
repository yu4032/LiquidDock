package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * SecurityCenter/Game Turbo sidebar liquid-glass bridge.
 *
 * The expanded sidebar and its All Apps panel live in one TurboLayout / one type=2003 window.
 * Keep one PassBlur producer, but size the TextureView host to the live union of the verified
 * DockLayout and All Apps material owners rather than assuming the TurboLayout root is panel-sized.
 */
final class SecurityCenterSidebarGlassHook {
    private static final String TAG = "[DC][SidebarGlass]";
    private static final String TURBO_LAYOUT =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";
    private static final String ALL_APPS_MATERIAL_HELPER = "com.miui.dock.allapps.f0";
    private static final float GLASS_Z = -2f;
    private static final float DEFAULT_RADIUS_DP = 28f;
    private static final float SQUIRCLE_CP = .58f;
    private static final int TARGET_READY_FRAMES = 120;
    private static final int ZERO_COPY_VALIDATION_FRAMES = 90;

    private static boolean installed;
    private static final WeakHashMap<ViewGroup, Boolean> trackedRoots = new WeakHashMap<>();
    private static final WeakHashMap<View, Drawable> originalMaterialBackgrounds =
            new WeakHashMap<>();
    private static final WeakHashMap<View, GradientDrawable> transparentMaterialBackgrounds =
            new WeakHashMap<>();
    private static WeakReference<ViewGroup> rootRef = new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef = new WeakReference<>(null);
    private static WeakReference<ViewTreeObserver> layoutObserverRef = new WeakReference<>(null);
    private static ViewTreeObserver.OnPreDrawListener layoutListener;

    private SecurityCenterSidebarGlassHook() {}

    static synchronized boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        if (classLoader == null || config == null) return false;
        try {
            Class<?> turbo = Class.forName(TURBO_LAYOUT, false, classLoader);
            Constructor<?>[] constructors = turbo.getDeclaredConstructors();
            if (constructors.length == 0) return false;
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (owner instanceof ViewGroup && GlassRuntimeState.isEnabled()) {
                        trackTurboLayout((ViewGroup) owner, config);
                    }
                    return result;
                });
            }
            hookVendorMaterialReset(turbo);
            hookAllAppsMaterialReset(classLoader);
            installed = true;
            MainHook.log(TAG + " TurboLayout constructor hooks installed count="
                    + constructors.length);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " install failed: " + error);
            return false;
        }
    }

    /** TurboLayout.U() reapplies the native DockLayout blur/material on the verified build. */
    private static void hookVendorMaterialReset(Class<?> turbo) {
        try {
            Method reset = turbo.getDeclaredMethod("U");
            reset.setAccessible(true);
            HookUtil.hook(reset, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) {
                    ViewGroup root = (ViewGroup) owner;
                    root.post(() -> resuppressIfActive(root));
                }
                return result;
            });
            MainHook.log(TAG + " TurboLayout.U material reset hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " TurboLayout.U material reset hook unavailable: " + error);
        }
    }

    /** com.miui.dock.allapps.f0.c(w, Context) is the All Apps blur/material initializer. */
    private static void hookAllAppsMaterialReset(ClassLoader classLoader) {
        try {
            Class<?> helper = Class.forName(ALL_APPS_MATERIAL_HELPER, false, classLoader);
            Class<?> allApps = Class.forName(
                    SidebarGlassPolicy.ALL_APPS_LAYOUT_CLASS, false, classLoader);
            Method reset = helper.getDeclaredMethod("c", allApps, Context.class);
            reset.setAccessible(true);
            HookUtil.hook(reset, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length > 0 && args[0] instanceof View) {
                    View material = (View) args[0];
                    material.post(() -> {
                        ViewGroup root = rootRef.get();
                        if (root != null && material.getRootView() == root.getRootView()
                                && Miuix307ZeroCopyRenderer.isActive()) {
                            suppressVendorMaterial(material);
                        }
                    });
                }
                return result;
            });
            MainHook.log(TAG + " All Apps material reset hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " All Apps material reset hook unavailable: " + error);
        }
    }

    private static void trackTurboLayout(ViewGroup root, LiquidDockConfig config) {
        synchronized (trackedRoots) {
            if (trackedRoots.put(root, Boolean.TRUE) != null) return;
        }
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                if (!GlassRuntimeState.isEnabled()) return;
                root.post(() -> bindWhenGameTurboReady(root, config, 0));
            }

            @Override public void onViewDetachedFromWindow(View view) {
                if (rootRef.get() != root) return;
                clearObservation();
                restoreVendorMaterials(root);
                rootRef = new WeakReference<>(null);
                hostRef = new WeakReference<>(null);
                // The TextureView releases EGL/producer resources from its own detach callback.
            }
        });
        if (root.isAttachedToWindow()) {
            root.post(() -> bindWhenGameTurboReady(root, config, 0));
        }
    }

    /**
     * TurboLayout also hosts conversation/video sidebars. The verified Game Turbo branch alone
     * creates both getGameTurboLayout() and the new DockLayout that owns the All Apps button.
     */
    private static void bindWhenGameTurboReady(
            ViewGroup root, LiquidDockConfig config, int frame) {
        if (!GlassRuntimeState.isEnabled() || root == null || !root.isAttachedToWindow()) return;
        View dock = dockLayout(root);
        if (!isGameTurboRoot(root) || !hasUsableBounds(dock)) {
            if (frame < TARGET_READY_FRAMES) {
                root.postOnAnimation(() -> bindWhenGameTurboReady(root, config, frame + 1));
            }
            return;
        }
        bindGameTurboSidebar(root, config, dock);
    }

    private static boolean isGameTurboRoot(ViewGroup root) {
        return HookUtil.invoke(root, "getGameTurboLayout") instanceof View
                && dockLayout(root) != null;
    }

    private static View dockLayout(ViewGroup root) {
        Object dock = HookUtil.invoke(root, "getDockLayout");
        return dock instanceof View ? (View) dock : null;
    }

    private static void bindGameTurboSidebar(
            ViewGroup root, LiquidDockConfig config, View dock) {
        ViewGroup current = rootRef.get();
        if (current != null && current != root && current.isAttachedToWindow()) return;

        DockLiquidGlassHostView currentHost = hostRef.get();
        if (current == root && currentHost != null && currentHost.getParent() == root) return;

        clearObservation();
        if (current != null && current != root) Miuix307ZeroCopyRenderer.clear();

        float radius = DEFAULT_RADIUS_DP * root.getResources().getDisplayMetrics().density;
        DockLiquidGlassHostView host = new DockLiquidGlassHostView(root.getContext());
        host.setId(View.generateViewId());
        host.setZ(GLASS_Z);
        host.setGeometry(radius, false, SQUIRCLE_CP);
        root.addView(host, 0, new ViewGroup.MarginLayoutParams(1, 1));
        if (!syncHostGeometry(root, host)) {
            root.removeView(host);
            return;
        }

        ViewGroup producerOwner = dock instanceof ViewGroup ? (ViewGroup) dock : root;
        boolean rendererInstalled = Miuix307ZeroCopyRenderer.install(
                producerOwner, host, config.glass, Math.round(config.glass.blur));
        if (!rendererInstalled) {
            root.removeView(host);
            MainHook.log(TAG + " PassBlur renderer unavailable; vendor material preserved");
            return;
        }

        rootRef = new WeakReference<>(root);
        hostRef = new WeakReference<>(host);
        scheduleZeroCopyValidation(root, host, 0);
        MainHook.log(TAG + " Game Turbo sidebar + All Apps Prismal host pending");
    }

    private static void scheduleZeroCopyValidation(
            ViewGroup root, DockLiquidGlassHostView host, int frame) {
        if (rootRef.get() != root || hostRef.get() != host || host.getParent() != root) return;
        syncHostGeometry(root, host);
        if (Miuix307ZeroCopyRenderer.isActive()) {
            suppressVendorMaterials(root);
            installMaterialObserver(root);
            MainHook.log(TAG + " zero-copy active; vendor sidebar materials suppressed");
            return;
        }
        if (Miuix307ZeroCopyRenderer.isActivationExhausted()
                || frame >= ZERO_COPY_VALIDATION_FRAMES) {
            Miuix307ZeroCopyRenderer.clear();
            if (host.getParent() == root) root.removeView(host);
            rootRef = new WeakReference<>(null);
            hostRef = new WeakReference<>(null);
            MainHook.log(TAG + " zero-copy inactive; vendor sidebar material preserved");
            return;
        }
        host.postOnAnimation(() -> scheduleZeroCopyValidation(root, host, frame + 1));
    }

    private static void resuppressIfActive(ViewGroup root) {
        if (root != rootRef.get() || !Miuix307ZeroCopyRenderer.isActive()) return;
        DockLiquidGlassHostView host = hostRef.get();
        if (host != null) syncHostGeometry(root, host);
        suppressVendorMaterials(root);
    }

    /** Keep one TextureView over the visible union of DockLayout and the optional All Apps panel. */
    private static boolean syncHostGeometry(ViewGroup root, DockLiquidGlassHostView host) {
        if (root == null || host == null || host.getParent() != root) return false;
        View dock = dockLayout(root);
        Rect union = rectInRoot(root, dock);
        if (union == null || union.width() <= 0 || union.height() <= 0) return false;

        View allApps = findDescendantByClass(root, SidebarGlassPolicy.ALL_APPS_LAYOUT_CLASS);
        Rect allAppsRect = rectInRoot(root, allApps);
        if (allAppsRect != null && allApps != null && allApps.getVisibility() == View.VISIBLE
                && allApps.getAlpha() > 0.01f) {
            union.union(allAppsRect);
        }

        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (lp == null) return false;
        boolean changed = lp.width != union.width() || lp.height != union.height();
        lp.width = union.width();
        lp.height = union.height();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
            changed |= margins.leftMargin != union.left || margins.topMargin != union.top;
            margins.leftMargin = union.left;
            margins.topMargin = union.top;
            host.setTranslationX(0f);
            host.setTranslationY(0f);
        } else {
            changed |= Float.compare(host.getX(), union.left) != 0
                    || Float.compare(host.getY(), union.top) != 0;
            host.setX(union.left);
            host.setY(union.top);
        }
        if (changed) host.setLayoutParams(lp);
        return true;
    }

    private static Rect rectInRoot(ViewGroup root, View target) {
        if (root == null || target == null || !target.isShown()
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        Rect targetRect = new Rect();
        Rect rootRect = new Rect();
        if (!target.getGlobalVisibleRect(targetRect) || !root.getGlobalVisibleRect(rootRect)) {
            return null;
        }
        targetRect.offset(-rootRect.left, -rootRect.top);
        return targetRect;
    }

    private static boolean hasUsableBounds(View view) {
        return view != null && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private static View findDescendantByClass(View view, String className) {
        if (view == null || className == null) return null;
        if (className.equals(view.getClass().getName())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findDescendantByClass(group.getChildAt(i), className);
            if (found != null) return found;
        }
        return null;
    }

    private static void installMaterialObserver(ViewGroup root) {
        clearObservation();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        WeakReference<ViewGroup> watched = new WeakReference<>(root);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            ViewGroup current = watched.get();
            if (current != null && current == rootRef.get()) {
                DockLiquidGlassHostView host = hostRef.get();
                if (host != null) syncHostGeometry(current, host);
                View allApps = findDescendantByClass(
                        current, SidebarGlassPolicy.ALL_APPS_LAYOUT_CLASS);
                if (allApps != null && !transparentMaterialBackgrounds.containsKey(allApps)) {
                    suppressVendorMaterial(allApps);
                }
            }
            return true;
        };
        observer.addOnPreDrawListener(listener);
        layoutObserverRef = new WeakReference<>(observer);
        layoutListener = listener;
    }

    private static void clearObservation() {
        ViewTreeObserver observer = layoutObserverRef.get();
        ViewTreeObserver.OnPreDrawListener listener = layoutListener;
        layoutObserverRef = new WeakReference<>(null);
        layoutListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void suppressVendorMaterials(ViewGroup root) {
        View dock = dockLayout(root);
        if (dock != null) suppressVendorMaterial(dock);
        View allApps = findDescendantByClass(root, SidebarGlassPolicy.ALL_APPS_LAYOUT_CLASS);
        if (allApps != null) suppressVendorMaterial(allApps);
    }

    private static void suppressVendorMaterial(View view) {
        if (view == null || !SidebarGlassPolicy.isVendorMaterialClassName(
                view.getClass().getName())) return;
        MiBlurBridge.clearPassWindowBlurIncludingBackgroundMode(view);
        GradientDrawable transparent = transparentMaterialBackgrounds.get(view);
        if (transparent == null) {
            if (!originalMaterialBackgrounds.containsKey(view)) {
                originalMaterialBackgrounds.put(view, view.getBackground());
            }
            transparent = new GradientDrawable();
            transparent.setShape(GradientDrawable.RECTANGLE);
            transparent.setColor(Color.TRANSPARENT);
            transparent.setCornerRadius(DEFAULT_RADIUS_DP
                    * view.getResources().getDisplayMetrics().density);
            transparentMaterialBackgrounds.put(view, transparent);
        }
        if (view.getBackground() != transparent) view.setBackground(transparent);
    }

    private static void restoreVendorMaterials(ViewGroup root) {
        View dock = dockLayout(root);
        if (dock != null) restoreVendorMaterial(dock);
        View allApps = findDescendantByClass(root, SidebarGlassPolicy.ALL_APPS_LAYOUT_CLASS);
        if (allApps != null) restoreVendorMaterial(allApps);
    }

    private static void restoreVendorMaterial(View view) {
        if (view == null || !originalMaterialBackgrounds.containsKey(view)) return;
        Drawable original = originalMaterialBackgrounds.remove(view);
        transparentMaterialBackgrounds.remove(view);
        view.setBackground(original);
    }
}
