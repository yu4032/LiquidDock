package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.ViewTreeObserver;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * The native laptop DockContainerView fills the Dock window. Add a non-touchable
 * backdrop behind its HotSeats; the existing Prismal compositor still owns icon glass.
 * The panel must not live inside the narrow HotSeats background (it would be clipped).
 */
final class WorkstationTaskbarBackgroundHook {
    private static final String LAPTOP_DOCK =
            "com.miui.home.launcher.laptop.dockbar.DockContainerView";
    private static final String PANEL_TAG = "LiquidDock.workstation.taskbar";
    private static final WeakHashMap<ViewGroup, Boolean> roots = new WeakHashMap<>();
    private static WeakReference<TaskbarPanel> activePanel = new WeakReference<>(null);
    private static volatile boolean enabled;
    private static volatile boolean coreEnabled = true;
    private static boolean installed;
    private static WeakReference<View> nativeMaterialRef = new WeakReference<>(null);
    private static Drawable nativeBackground;
    private static Boolean nativeBlurEnabled;
    private static final Drawable CLEAR_MATERIAL = new ColorDrawable(Color.TRANSPARENT);
    private static ViewTreeObserver nativeObserver;
    private static ViewTreeObserver.OnPreDrawListener nativeObserverCallback;

    private WorkstationTaskbarBackgroundHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig.Workstation config) {
        applyLiveConfig(config);
        if (installed) return;
        // This boundary must also exist when the zero-copy glass pipeline is not installed.
        LauncherVendorBlurWriteSuppressor.install();
        try {
            HookUtil.hookMethod(classLoader, LAPTOP_DOCK, "onAttachedToWindow", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof ViewGroup) {
                    ViewGroup root = (ViewGroup) chain.getThisObject();
                    synchronized (roots) { roots.put(root, Boolean.TRUE); }
                    root.post(() -> sync(root));
                }
                return result;
            });
            for (String background : new String[]{
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground"}) {
                try {
                    HookUtil.hookMethod(classLoader, background, "onAttachedToWindow", chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        refresh();
                        return result;
                    });
                } catch (Throwable ignored) {
                    // Device themes may provide only one of the two background classes.
                }
            }
            HookUtil.hookMethod(classLoader, LAPTOP_DOCK, "onDetachedFromWindow", chain -> {
                if (chain.getThisObject() instanceof ViewGroup) {
                    ViewGroup root = (ViewGroup) chain.getThisObject();
                    View material = nativeMaterialRef.get();
                    if (material != null && material.getRootView() == root.getRootView()) {
                        releaseNativeMaterial();
                    }
                    View panel = activePanel.get();
                    if (panel != null && panel.getParent() == root) activePanel.clear();
                    synchronized (roots) { roots.remove(root); }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            MainHook.log("[DC][WorkstationTaskbar] laptop Dock owner hooked");
        } catch (Throwable error) {
            MainHook.log("[DC][WorkstationTaskbar] no laptop Dock owner: " + error);
        }
    }

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        enabled = config.taskbarBackground;
        refresh();
    }

    static void onModeChanged() { refresh(); }

    static void onCoreEnabledChanged(boolean next) {
        if (coreEnabled == next) return;
        coreEnabled = next;
        refresh();
    }

    static boolean isReplacingDockBody() {
        TaskbarPanel panel = activePanel.get();
        return WorkstationTaskbarPolicy.shouldReplace(
                WorkstationRuntimeState.isActive(), enabled && coreEnabled,
                installed && panel != null
                        && panel.isAttachedToWindow() && panel.getVisibility() == View.VISIBLE);
    }

    /** The existing native material owner only; never suppress blur on the new panel. */
    static boolean isReplacingNativeMaterial(View view) {
        return view != null && view == nativeMaterialRef.get() && isReplacingDockBody();
    }

    private static void refresh() {
        ArrayList<ViewGroup> snapshot;
        synchronized (roots) { snapshot = new ArrayList<>(roots.keySet()); }
        for (ViewGroup root : snapshot) {
            if (root != null) root.post(() -> sync(root));
        }
    }

    private static void sync(ViewGroup root) {
        if (root == null || !root.isAttachedToWindow()) return;
        boolean show = enabled && coreEnabled && WorkstationRuntimeState.isActive();
        View existing = root.findViewWithTag(PANEL_TAG);
        // Default-off is completely inert: do not refresh the native Dock.
        if (!show && existing == null && nativeMaterialRef.get() == null) return;
        if (show && existing == null) {
            TaskbarPanel panel = new TaskbarPanel(root.getContext());
            panel.setTag(PANEL_TAG);
            panel.setClickable(false);
            panel.setFocusable(false);
            panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int height = Math.round(96f * root.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
            root.addView(panel, 0, lp);
            activePanel = new WeakReference<>(panel);
        } else if (show && existing instanceof TaskbarPanel) {
            activePanel = new WeakReference<>((TaskbarPanel) existing);
        }

        // When glass is disabled, this is an actual replacement of the vendor native
        // background, not a second blur drawn behind its still-active rounded plate.
        // Release ownership before removing the panel so OEM state can be restored.
        if (WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                WorkstationRuntimeState.isActive(), enabled && coreEnabled,
                activePanel.get() != null && activePanel.get().isAttachedToWindow(),
                GlassRuntimeState.isEnabled())) {
            View material = findNativeMaterial(root);
            if (material != null) bindNativeMaterial(material);
            else releaseNativeMaterial();
        } else {
            releaseNativeMaterial();
        }
        if (!show && existing != null) {
            MiBlurBridge.clearPassWindowBlur(existing);
            root.removeView(existing);
            if (activePanel.get() == existing) activePanel.clear();
        }

        // The body and icons share an EGL surface. Never hide the TextureView.
        Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
        DockStrokeRenderer.refreshInstalledFromCurrentConfig();
        DockShadowOwnership.refreshVendorDockShadow();
    }

    private static View findNativeMaterial(ViewGroup root) {
        View owner = DockShadowOwnership.activeBackground();
        if (owner != null && owner.isAttachedToWindow()
                && owner.getRootView() == root.getRootView()
                && isMaterialClass(owner)) return owner;
        return findMaterialChild(root);
    }

    private static View findMaterialChild(View view) {
        if (isMaterialClass(view)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) view;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View found = findMaterialChild(parent.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isMaterialClass(View view) {
        if (view == null) return false;
        String name = view.getClass().getName();
        return name.equals("com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2")
                || name.equals("com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground");
    }

    private static void bindNativeMaterial(View material) {
        if (material == nativeMaterialRef.get()) return;
        releaseNativeMaterial();
        nativeMaterialRef = new WeakReference<>(material);
        nativeBackground = material.getBackground();
        nativeBlurEnabled = MiBlurBridge.getPassWindowBlurEnabled(material);
        // Both verified OEM material classes enable blur at attach. On builds where
        // the hidden getter is absent, restoring their original blur gate to true
        // is safer than leaving the native Dock permanently unblurred.
        if (nativeBlurEnabled == null) nativeBlurEnabled = Boolean.TRUE;
        material.setBackground(CLEAR_MATERIAL);
        // Preserve the vendor's blur radius, blend colours and mode for exact restoration.
        MiBlurBridge.setPassWindowBlurEnabled(material, false);

        ViewTreeObserver observer = material.getViewTreeObserver();
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            View current = nativeMaterialRef.get();
            if (current == material && isReplacingDockBody()) {
                if (current.getBackground() != CLEAR_MATERIAL) {
                    // Retain the most recent OEM drawable for restoration.
                    nativeBackground = current.getBackground();
                    current.setBackground(CLEAR_MATERIAL);
                }
            }
            return true;
        };
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(listener);
            nativeObserver = observer;
            nativeObserverCallback = listener;
        }
    }

    private static void releaseNativeMaterial() {
        View material = nativeMaterialRef.get();
        nativeMaterialRef = new WeakReference<>(null);
        if (nativeObserver != null && nativeObserverCallback != null) {
            try {
                if (nativeObserver.isAlive()) {
                    nativeObserver.removeOnPreDrawListener(nativeObserverCallback);
                }
            } catch (Throwable ignored) {}
        }
        nativeObserver = null;
        nativeObserverCallback = null;
        if (material != null) {
            if (material.getBackground() == CLEAR_MATERIAL) {
                material.setBackground(nativeBackground);
            }
            if (nativeBlurEnabled != null) {
                MiBlurBridge.setPassWindowBlurEnabled(material, nativeBlurEnabled);
            }
        }
        nativeBackground = null;
        nativeBlurEnabled = null;
    }

    private static final class TaskbarPanel extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float lineWidth;

        TaskbarPanel(Context context) {
            super(context);
            setWillNotDraw(false);
            lineWidth = Math.max(1f, context.getResources().getDisplayMetrics().density * .6f);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            int radius = Math.round(42f * getResources().getDisplayMetrics().density);
            MiBlurBridge.applyPassWindowBlur(this, radius);
            Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
        }

        @Override protected void onDetachedFromWindow() {
            MiBlurBridge.clearPassWindowBlur(this);
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Reference: edge-to-edge dark translucent bar, thin light upper rule.
            // PassWindowBlur supplies live wallpaper diffusion when supported.
            paint.setColor(Color.argb(90, 17, 23, 25));
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
            paint.setColor(Color.argb(64, 228, 233, 228));
            canvas.drawRect(0f, 0f, getWidth(), lineWidth, paint);
        }
    }
}
