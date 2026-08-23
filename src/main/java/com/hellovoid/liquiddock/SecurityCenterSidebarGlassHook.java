package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.WeakHashMap;

/**
 * SecurityCenter/Game Turbo sidebar liquid-glass bridge.
 *
 * The expanded sidebar and its All Apps panel live in one TurboLayout / one type=2003 window.
 * Keep one PassBlur producer and one Prismal TextureView behind the vendor content. All Apps is
 * dynamically inserted later at Z=-1, so the glass host lives at Z=-2 and naturally remains
 * behind both panels without a second producer.
 */
final class SecurityCenterSidebarGlassHook {
    private static final String TAG = "[DC][SidebarGlass]";
    private static final String TURBO_LAYOUT =
            "com.miui.gamebooster.windowmanager.newbox.TurboLayout";
    private static final String ALL_APPS_LAYOUT = "com.miui.dock.allapps.w";
    private static final float GLASS_Z = -2f;
    private static final float DEFAULT_RADIUS_DP = 28f;
    private static final float SQUIRCLE_CP = .58f;
    private static final int TARGET_READY_FRAMES = 120;
    private static final int ZERO_COPY_VALIDATION_FRAMES = 90;

    private static boolean installed;
    private static final WeakHashMap<ViewGroup, Boolean> trackedRoots = new WeakHashMap<>();
    private static WeakReference<ViewGroup> rootRef = new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef = new WeakReference<>(null);
    private static WeakReference<ViewGroup> opticsOwnerRef = new WeakReference<>(null);
    private static Drawable originalRootBackground;
    private static WeakReference<ViewTreeObserver> layoutObserverRef = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener layoutListener;

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
            installed = true;
            MainHook.log(TAG + " TurboLayout constructor hooks installed count="
                    + constructors.length);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " install failed: " + error);
            return false;
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
                rootRef = new WeakReference<>(null);
                hostRef = new WeakReference<>(null);
                clearOpticsMetadata(root, false);
                // The TextureView shuts its EGL/producer resources down from its own detach.
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
        if (!isGameTurboRoot(root)) {
            if (frame < TARGET_READY_FRAMES) {
                root.postOnAnimation(() -> bindWhenGameTurboReady(root, config, frame + 1));
            }
            return;
        }
        bindGameTurboSidebar(root, config);
    }

    private static boolean isGameTurboRoot(ViewGroup root) {
        return HookUtil.invoke(root, "getGameTurboLayout") instanceof View
                && HookUtil.invoke(root, "getDockLayout") instanceof View;
    }

    private static void bindGameTurboSidebar(ViewGroup root, LiquidDockConfig config) {
        ViewGroup current = rootRef.get();
        if (current != null && current != root && current.isAttachedToWindow()) return;

        DockLiquidGlassHostView currentHost = hostRef.get();
        if (current == root && currentHost != null && currentHost.getParent() == root) return;

        clearObservation();
        if (current != null && current != root) {
            Miuix307ZeroCopyRenderer.clear();
            clearOpticsMetadata(current, false);
        }

        float radius = DEFAULT_RADIUS_DP * root.getResources().getDisplayMetrics().density;
        installOpticsMetadata(root, radius);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(root.getContext());
        host.setId(View.generateViewId());
        host.setZ(GLASS_Z);
        host.setGeometry(radius, false, SQUIRCLE_CP);
        root.addView(host, 0, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean rendererInstalled = Miuix307ZeroCopyRenderer.install(
                root, host, config.glass, Math.round(config.glass.blur));
        if (!rendererInstalled) {
            root.removeView(host);
            clearOpticsMetadata(root, true);
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
            clearOpticsMetadata(root, true);
            rootRef = new WeakReference<>(null);
            hostRef = new WeakReference<>(null);
            MainHook.log(TAG + " zero-copy inactive; vendor sidebar material preserved");
            return;
        }
        host.postOnAnimation(() -> scheduleZeroCopyValidation(root, host, frame + 1));
    }

    /** Transparent rounded metadata lets the shared Dock renderer reuse a sidebar-sized radius. */
    private static void installOpticsMetadata(ViewGroup root, float radius) {
        if (opticsOwnerRef.get() != root) {
            originalRootBackground = root.getBackground();
            opticsOwnerRef = new WeakReference<>(root);
        }
        GradientDrawable metadata = new GradientDrawable();
        metadata.setShape(GradientDrawable.RECTANGLE);
        metadata.setColor(Color.TRANSPARENT);
        metadata.setCornerRadius(Math.max(0f, radius));
        root.setBackground(metadata);
    }

    private static void clearOpticsMetadata(ViewGroup root, boolean restore) {
        if (root == null || opticsOwnerRef.get() != root) return;
        if (restore) root.setBackground(originalRootBackground);
        opticsOwnerRef = new WeakReference<>(null);
        originalRootBackground = null;
    }

    private static void installMaterialObserver(ViewGroup root) {
        clearObservation();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        WeakReference<ViewGroup> watched = new WeakReference<>(root);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            ViewGroup current = watched.get();
            if (current == null || current != rootRef.get()) return;
            suppressVendorMaterials(current);
        };
        observer.addOnGlobalLayoutListener(listener);
        layoutObserverRef = new WeakReference<>(observer);
        layoutListener = listener;
    }

    private static void clearObservation() {
        ViewTreeObserver observer = layoutObserverRef.get();
        ViewTreeObserver.OnGlobalLayoutListener listener = layoutListener;
        layoutObserverRef = new WeakReference<>(null);
        layoutListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    /**
     * Remove only the two vendor material bodies that LiquidDock replaces. Internal All Apps
     * cards/search pills are intentionally untouched.
     */
    private static void suppressVendorMaterials(View view) {
        if (view == null) return;
        if (isReplacedMaterial(view)) {
            MiBlurBridge.clearPassWindowBlur(view);
            if (view.getBackground() != null) view.setBackgroundColor(Color.TRANSPARENT);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == hostRef.get()) continue;
            suppressVendorMaterials(child);
        }
    }

    private static boolean isReplacedMaterial(View view) {
        if (view == null) return false;
        if (ALL_APPS_LAYOUT.equals(view.getClass().getName())) return true;
        int id = view.getId();
        if (id == View.NO_ID) return false;
        try {
            return "sidebar_panel".equals(view.getResources().getResourceEntryName(id));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
