package com.hellovoid.liquiddock;

import android.graphics.Color;
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
 * Keep one PassBlur producer and one Prismal TextureView behind the vendor content.  All Apps is
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

    private static boolean installed;
    private static final WeakHashMap<ViewGroup, Boolean> trackedRoots = new WeakHashMap<>();
    private static WeakReference<ViewGroup> rootRef = new WeakReference<>(null);
    private static WeakReference<DockLiquidGlassHostView> hostRef = new WeakReference<>(null);
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
                root.post(() -> bindFirstLiveSidebar(root, config));
            }

            @Override public void onViewDetachedFromWindow(View view) {
                if (rootRef.get() != root) return;
                clearObservation();
                rootRef = new WeakReference<>(null);
                hostRef = new WeakReference<>(null);
                // The TextureView shuts its EGL/producer resources down from its own detach.
            }
        });
        if (root.isAttachedToWindow()) root.post(() -> bindFirstLiveSidebar(root, config));
    }

    private static void bindFirstLiveSidebar(ViewGroup root, LiquidDockConfig config) {
        if (!GlassRuntimeState.isEnabled() || root == null || !root.isAttachedToWindow()) return;

        ViewGroup current = rootRef.get();
        if (current != null && current != root && current.isAttachedToWindow()) {
            // SecurityCenter can construct an assistant TurboLayout too. The main sidebar is
            // created first on the verified build; keep one producer bound to that live owner.
            return;
        }
        DockLiquidGlassHostView currentHost = hostRef.get();
        if (current == root && currentHost != null && currentHost.getParent() == root) {
            suppressVendorMaterials(root);
            return;
        }

        clearObservation();
        if (current != null && current != root) Miuix307ZeroCopyRenderer.clear();

        suppressVendorMaterials(root);

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(root.getContext());
        host.setId(View.generateViewId());
        host.setZ(GLASS_Z);
        float radius = DEFAULT_RADIUS_DP * root.getResources().getDisplayMetrics().density;
        host.setGeometry(radius, false, SQUIRCLE_CP);
        root.addView(host, 0, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean rendererInstalled = Miuix307ZeroCopyRenderer.install(
                root, host, config.glass, Math.round(config.glass.blur));
        if (!rendererInstalled) {
            root.removeView(host);
            MainHook.log(TAG + " PassBlur renderer unavailable");
            return;
        }

        rootRef = new WeakReference<>(root);
        hostRef = new WeakReference<>(host);
        installMaterialObserver(root);
        MainHook.log(TAG + " shared sidebar + All Apps Prismal host attached");
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
